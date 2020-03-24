package mcextract;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;

@Mod(modid = "mcextract", name = "McExtract", version = "0.1.0")
public class Main {
//	public static void main(String[] args) {
//		System.err.println("Initializing Minecraft registries ...");
//		Bootstrap.register();
//
//		extractStuff();
//	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		MinecraftForge.EVENT_BUS.register(this);
	}

	boolean ran = false;

	@SubscribeEvent
	public void onJoinWorld(PlayerSetSpawnEvent event) {
		if (ran) return;
		ran = true;
		try {
			System.err.println("extracting stuff ...");
			extractStuff(Minecraft.getMinecraft().world);
		} catch (Exception e) {
			ran = false;
			e.printStackTrace();
		}
	}

	public static void extractStuff(World world) {
		final boolean write = true;

		final String mcVersion = Minecraft.getMinecraft().getVersion();
		System.err.println("Done for Minecraft " + mcVersion);

		System.err.println("Extracting Block data ...");
		final JsonObject allBlocksJson = extractBlockCollisionShapes(world);
		System.err.println("Extracted data for " + allBlocksJson.getAsJsonObject("blocks").size() + " blocks"
				+ " and " + allBlocksJson.getAsJsonObject("shapes").size() + " distinct shapes.");

		if (write) {
			String outString = allBlocksJson.toString();
			outString = outString.replaceAll("\\},|null,|\"[_a-zA-Z]+\":[0-9]+,", "$0\n");
			outString = outString.replaceAll("\\],\"", "],\n\"");

			String nowIso = ISO_DATE_TIME.format(LocalDateTime.now());
			String outPath = "collisionShapes_" + mcVersion + "_" + nowIso + ".json";
			System.err.println("Writing " + outPath + " ...");
			try {
				Files.write(Paths.get(outPath), outString.getBytes(UTF_8));
				System.err.println("Done.");
			} catch (IOException e) {
				e.printStackTrace();
				System.err.println("Failed.");
			}
		}

		System.err.println("Running sanity checks ...");
		final ArrayList<String> failures = runSanityChecks(new BlockCollisionBoxStorage(allBlocksJson), world);
		System.err.println(failures.size() + " failures.");
	}

	/**
	 * output data structure:
	 * <pre>
	 * {
	 *   "shapes": { [shape]: [[x1,y1,z1, x2,y2,z2], ...] },
	 *   "blocks": {
	 *     [blockIdNum]: shape, // all block states have same shape
	 *     [blockIdNum]: [ shape, ... ], // at least one state shape different from others; indexed by meta
	 *     ...
	 *   }
	 * }
	 * </pre>
	 */
	private static JsonObject extractBlockCollisionShapes(World world) {
		final JsonObject allBlocksJson = new JsonObject();
		final HashMap<Shape, Integer> shapeIds = new HashMap<>();

		for (final Block block : Block.REGISTRY) {
			try {
				final ImmutableList<IBlockState> states = block.getBlockState().getValidStates();
				final int[] boxesByState = new int[16];

				final Shape state0Shape = new Shape(block.getDefaultState(), world);
				final Integer state0ShapeId = lookupAndPersistShapeId(state0Shape, shapeIds);
				boolean allSame = true;

				for (int stateId = 0; stateId < states.size(); stateId++) {
					IBlockState blockState = states.get(stateId);
					try {
						final Shape stateShape = new Shape(blockState, world);
						final Integer stateShapeId = lookupAndPersistShapeId(stateShape, shapeIds);
						boxesByState[block.getMetaFromState(blockState)] = stateShapeId;

						if (allSame && !Objects.equals(state0ShapeId, stateShapeId)) {
							allSame = false;
						}
					} catch (Exception e) {
						System.err.println("in blockState " + blockState);
						e.printStackTrace();
					}
				}

				final String blockId = getBlockStateIdString(block);
				if (allSame) {
					allBlocksJson.addProperty(blockId, state0ShapeId);
				} else {
					final JsonArray blockJson = new JsonArray();
					for (int shapeId : boxesByState) {
						blockJson.add(shapeId);
					}
					allBlocksJson.add(blockId, blockJson);

					System.err.println(blockId + ": " + boxesByState.length + " states, "
							+ (boxesByState.length - blockJson.size()) + " empty");
				}
			} catch (Exception e) {
				System.err.println("in block " + block);
				e.printStackTrace();
			}
		}

		final JsonObject allShapesJson = new JsonObject();
		shapeIds.entrySet().stream().sorted(Map.Entry.comparingByValue())
				.forEach(entry -> allShapesJson.add(entry.getValue().toString(), entry.getKey().toJson()));

		final JsonObject rootJson = new JsonObject();
		rootJson.add("blocks", allBlocksJson);
		rootJson.add("shapes", allShapesJson);
		return rootJson;
	}

	private static ArrayList<String> runSanityChecks(BlockCollisionBoxStorage storage, World world) {
		final ArrayList<String> failures = new ArrayList<>();
		for (final Block block : Block.REGISTRY) {
			final String blockId = getBlockStateIdString(block);
			final ImmutableList<IBlockState> states = block.getBlockState().getValidStates();
			for (int stateId = 0; stateId < states.size(); stateId++) {
				IBlockState blockState = states.get(stateId);
				try {
					final Shape mcShape = new Shape(blockState, world);
					final Shape storageShape = storage.getBlockShape(blockId, stateId);
					if (!mcShape.equals(storageShape)) {
						System.err.println("ERROR: shapes differ: block=" + blockId + " state=" + stateId);
						failures.add(blockId + ":" + stateId);
					}
				} catch (Exception e) {
					System.err.println("in blockState " + blockState);
					e.printStackTrace();
				}
			}
		}
		return failures;
	}

	private static Integer lookupAndPersistShapeId(Shape shape, HashMap<Shape, Integer> shapeIds) {
//		if (shape.boxes.isEmpty()) return null;
		return shapeIds.computeIfAbsent(shape, s -> shapeIds.size());
	}

	private static String getBlockStateIdString(Block block) {
		final String blockId = Block.REGISTRY.getNameForObject(block).toString();
		if (!blockId.startsWith("minecraft:")) return blockId;
		return blockId.replaceAll("^minecraft:", "");
	}
}
