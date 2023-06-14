package mcextract;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;

import java.lang.reflect.Field;

public class Main {
	public static String getGameVersion() {
		return SharedConstants.getCurrentVersion().getName();
	}

	public static String getArg(String[] args, String argName, String defaultValue) {
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals(argName)) {
				return args[i + 1];
			}
		}
		return defaultValue;
	}

	// Static method to get a private field from a class
	public static <T, E> T getPrivateValue(Class <? super E > classToAccess, E instance, String fieldName) {
		try {
			Field field = classToAccess.getDeclaredField(fieldName);
			field.setAccessible(true);
			return (T) field.get(instance);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) {
		final boolean write = true;

		SharedConstants.tryDetectVersion();
		final String mcVersion = getGameVersion();
		System.err.println("Initializing Minecraft " + mcVersion + " registries ...");
		Bootstrap.bootStrap();
		System.err.println("Done.");

		// Get --outputDir from args
		String outputDir = getArg(args, "--outputDir", "");
		if (outputDir.length() > 0 && !outputDir.endsWith("/")) {
			outputDir += "/";
		}
		// make the output directory if it doesn't exist
		try {
			Files.createDirectories(Paths.get(outputDir));
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("Failed to create output directory.");
		}
		System.err.println("Extraction output directory: " + outputDir);

		JsonObject allBlocksJson = doBlockShapeExtraction();
		JsonObject allAttributesJson = doAttributeExtraction();

		if (write) {
			// Block shapes
			try {
				writeBlockShapes(outputDir, allBlocksJson);		
				System.err.println("Done with block shapes.");
			} catch (IOException e) {
				e.printStackTrace();
				System.err.println("Failed to write block shapes.");
			}

			// Attributes
			try {
				writeAttributes(outputDir, allAttributesJson);
				System.err.println("Done with attributes.");
			} catch (IOException e) {
				e.printStackTrace();
				System.err.println("Failed to write attributes.");
			}
		}

		System.err.println("Running collision sanity checks ...");
		final ArrayList<String> failures = runSanityChecks(new BlockCollisionBoxStorage(allBlocksJson));
		System.err.println(failures.size() + " failures.");
	}

	public static JsonObject doBlockShapeExtraction() {
		System.err.println("Extracting Block data ...");
		final JsonObject allBlocksJson = extractBlockCollisionShapes();
		System.err.println("Extracted data for " + allBlocksJson.getAsJsonObject("blocks").size() + " blocks"
			+ " and " + allBlocksJson.getAsJsonObject("shapes").size() + " distinct shapes.");
		return allBlocksJson;
	}

	public static void writeBlockShapes(String outputDir, JsonObject allBlocksJson) throws IOException {
		String outString = allBlocksJson.toString();
		outString = outString.replaceAll("\\},|null,|\"[_a-zA-Z]+\":[0-9]+,", "$0\n");
		outString = outString.replaceAll("\\],\"", "],\n\"");

		String nowIso = ISO_DATE_TIME.format(LocalDateTime.now());
		String outPath = outputDir + "blockCollisionShapes.json";
		System.err.println("Writing block shapes to '" + outPath + "'...");
		Files.write(Paths.get(outPath), outString.getBytes(UTF_8));
	}

	public static JsonObject doAttributeExtraction() {
		System.err.println("Extracting Attribute data ...");
		final JsonObject allAttributesJson = extractAttributes();
		System.err.println("Extracted data for " + allAttributesJson.size() + " attributes.");
		return allAttributesJson;
	}

	public static void writeAttributes(String outputDir, JsonObject allAttributesJson) throws IOException {
		// Pretty pritned JSON
		String outString = allAttributesJson.toString();
		outString = outString.replaceAll("\\},|null,|\"[_a-zA-Z]+\":[0-9]+,", "$0\n");
		String nowIso = ISO_DATE_TIME.format(LocalDateTime.now());
		String outPath = outputDir + "attributes.json";
		System.err.println("Writing attributes to '" + outPath + "'...");
		Files.write(Paths.get(outPath), outString.getBytes(UTF_8));
	}

	// COLLISIONS

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

		for (final Block block : BuiltInRegistries.BLOCK) {
			final ImmutableList<BlockState> states = block.getStateDefinition().getPossibleStates();
			final int[] boxesByState = new int[states.size()];

			final Shape state0Shape = new Shape(block.defaultBlockState());
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
		for (final Block block : BuiltInRegistries.BLOCK) {
			final String blockId = getBlockIdString(block);
			final ImmutableList<BlockState> states = block.getStateDefinition().getPossibleStates();
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
		final String blockId = Integer.toString(BuiltInRegistries.BLOCK.getId(block));
		if (!blockId.startsWith("minecraft:")) return blockId;
		return blockId.replaceAll("^minecraft:", "");
	}

	// ATTRIBUTES
	public static JsonObject extractAttributes() {
		final JsonObject allAttributes = new JsonObject();
		for (Attribute attr : BuiltInRegistries.ATTRIBUTE) {
			final JsonObject attrJson = new JsonObject();
			attrJson.addProperty("default", attr.getDefaultValue());
			if (attr instanceof RangedAttribute) {
				final RangedAttribute rangedAttr = (RangedAttribute) attr;
				double minVal = getPrivateValue(RangedAttribute.class, rangedAttr, "minValue");
				double maxVal = getPrivateValue(RangedAttribute.class, rangedAttr, "maxValue");
				attrJson.addProperty("min", minVal);
				attrJson.addProperty("max", maxVal);
			}

			ResourceLocation key = BuiltInRegistries.ATTRIBUTE.getKey(attr);
			if (key == null) {
				System.err.println("ERROR: attribute has no key: " + attr);
				continue;
			}
			attrJson.addProperty("description", attr.getDescriptionId());
			attrJson.addProperty("id", BuiltInRegistries.ATTRIBUTE.getId(attr));
			allAttributes.add(key.toString(), attrJson);
		}
		return allAttributes;
	}
}
