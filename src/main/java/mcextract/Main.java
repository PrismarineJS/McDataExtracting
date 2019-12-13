package mcextract;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.Bootstrap;
import net.minecraft.MinecraftVersion;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.registry.Registry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;

public class Main {
	public static void main(String[] args) {
		final boolean write = true;

		final String mcVersion = new MinecraftVersion().getName();
		System.err.println("Initializing Minecraft " + mcVersion + " registries ...");
		Bootstrap.initialize();
		System.err.println("Done.");

		System.err.println("Extracting Block data ...");
		final JsonObject allBlocksJson = extractBlockCollisionShapes();
		System.err.println("Extracted data for " + allBlocksJson.getAsJsonObject("blocks").size() + " blocks"
				+ " and " + allBlocksJson.getAsJsonObject("shapes").size() + " distinct shapes.");

		if (write) {
			String outString = allBlocksJson.toString();
			outString = outString.replaceAll("\\},|null,|\"[_a-zA-Z]+\":[0-9]+,", "$0\n");
			outString = outString.replaceAll("\\],\"", "],\n\"");

			String nowIso = ISO_DATE_TIME.format(LocalDateTime.now());
			String outPath = "block_collision_shapes_" + mcVersion + "_" + nowIso + ".json";
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
		final ArrayList<String> failures = runSanityChecks(new BlockCollisionBoxStorage(allBlocksJson));
		System.err.println(failures.size() + " failures.");
	}

	/**
	 * output data structure:
	 * <pre>
	 * {
	 *   "shapes": { [shape]: [[x1,y1,z1, x2,y2,z2], ...] },
	 *   "blocks": {
	 *     [block]: shape, // all block states have same shape
	 *     [block]: [ shape, ... ], // at least one state shape different from others; indexed by stateId
	 *     ...
	 *   }
	 * }
	 * </pre>
	 */
	private static JsonObject extractBlockCollisionShapes() {
		final JsonObject allBlocksJson = new JsonObject();
		final HashMap<Shape, Integer> shapeIds = new HashMap<>();

		for (final Block block : Registry.BLOCK) {
			final ImmutableList<BlockState> states = block.getStateFactory().getStates();
			final int[] boxesByState = new int[states.size()];

			final Shape state0Shape = new Shape(block.getDefaultState());
			final Integer state0ShapeId = lookupAndPersistShapeId(state0Shape, shapeIds);
			boolean allSame = true;

			for (int stateId = 0; stateId < states.size(); stateId++) {
				BlockState blockState = states.get(stateId);

				final Shape stateShape = new Shape(blockState);
				final Integer stateShapeId = lookupAndPersistShapeId(stateShape, shapeIds);
				boxesByState[stateId] = stateShapeId;

				if (allSame && !Objects.equals(state0ShapeId, stateShapeId)) {
					allSame = false;
				}
			}

			final String blockId = getBlockIdString(block);
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
		}

		final JsonObject allShapesJson = new JsonObject();
		shapeIds.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getValue))
				.forEach(entry -> allShapesJson.add(entry.getValue().toString(), entry.getKey().toJson()));

		final JsonObject rootJson = new JsonObject();
		rootJson.add("blocks", allBlocksJson);
		rootJson.add("shapes", allShapesJson);
		return rootJson;
	}

	private static ArrayList<String> runSanityChecks(BlockCollisionBoxStorage storage) {
		final ArrayList<String> failures = new ArrayList<>();
		for (final Block block : Registry.BLOCK) {
			final String blockId = getBlockIdString(block);
			final ImmutableList<BlockState> states = block.getStateFactory().getStates();
			for (int stateId = 0; stateId < states.size(); stateId++) {
				BlockState blockState = states.get(stateId);

				final Shape mcShape = new Shape(blockState);

				final Shape storageShape = storage.getBlockShape(blockId, stateId);

				if (!mcShape.equals(storageShape)) {
					System.err.println("ERROR: shapes differ: block=" + blockId + " state=" + stateId);
					failures.add(blockId + ":" + stateId);
				}
			}
		}
		return failures;
	}

	private static Integer lookupAndPersistShapeId(Shape shape, HashMap<Shape, Integer> shapeIds) {
		return shapeIds.computeIfAbsent(shape, s -> shapeIds.size());
	}

	private static String getBlockIdString(Block block) {
		final String blockId = Registry.BLOCK.getId(block).toString();
		if (!blockId.startsWith("minecraft:")) return blockId;
		return blockId.replaceAll("^minecraft:", "");
	}
}
