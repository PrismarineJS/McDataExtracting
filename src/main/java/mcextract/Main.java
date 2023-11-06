package mcextract;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    public static String getGameVersion() {
        SharedConstants.tryDetectVersion();
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

    public static void main(String[] args) {
        final boolean write = true;

        var mcVersion = getGameVersion();
        System.err.println("Initializing Minecraft " + mcVersion + " registries ...");
        Bootstrap.bootStrap();
        System.err.println("Done.");

        // Get --outputDir from args
        var outputDir = getArg(args, "--outputDir", "");
        if (!outputDir.isEmpty() && !outputDir.endsWith("/")) {
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

        var blockShapesJson = doBlockShapeExtraction();
        var blockPropertiesJson = doBlockPropertyExtraction();
        var attributesJson = doAttributeExtraction();

        if (write) {
            // Block shapes
            try {
                writeJson(outputDir, "blockCollisionShapes.json", blockShapesJson);
                System.err.println("Done with block shapes.");
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Failed to write block shapes.");
            }

            // Blocks
            try {
                writeJson(outputDir, "blockProperties.json", blockPropertiesJson);
                System.err.println("Done with block shapes.");
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Failed to write block shapes.");
            }

            // Attributes
            try {
                writeJson(outputDir, "attributes.json", attributesJson);
                System.err.println("Done with attributes.");
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Failed to write attributes.");
            }
        }

        System.err.println("Running collision sanity checks ...");
        var failures = runSanityChecks(new BlockCollisionBoxStorage(blockShapesJson));
        System.err.println(failures.size() + " failures.");
    }

    public static JsonObject doBlockShapeExtraction() {
        System.err.println("Extracting Block shape data ...");
        var blockShapesJson = extractBlockCollisionShapes();
        System.err.println("Extracted data for " + blockShapesJson.getAsJsonObject("blocks").size() + " blocks"
            + " and " + blockShapesJson.getAsJsonObject("shapes").size() + " distinct shapes.");
        return blockShapesJson;
    }

    public static JsonObject doBlockPropertyExtraction() {
        System.err.println("Extracting Block data ...");
        var blockPropertiesJson = extractBlockProperties();
        System.err.println("Extracted data for " + blockPropertiesJson.size() + " blocks.");
        return blockPropertiesJson;
    }

    public static JsonObject doAttributeExtraction() {
        System.err.println("Extracting Attribute data ...");
        var attributesJson = extractAttributes();
        System.err.println("Extracted data for " + attributesJson.size() + " attributes.");
        return attributesJson;
    }

    public static void writeJson(String outputDir, String fileName, JsonObject json) throws IOException {
        var outPath = outputDir + fileName;
        System.err.println("Writing to '" + outPath + "'...");
        Files.writeString(Paths.get(outPath), GSON.toJson(json));
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
        var allBlocksJson = new JsonObject();
        var shapeIds = new HashMap<Shape, Integer>();

        for (var block : BuiltInRegistries.BLOCK) {
            var states = block.getStateDefinition().getPossibleStates();
            var boxesByState = new int[states.size()];

            var state0Shape = new Shape(block.defaultBlockState());
            var state0ShapeId = lookupAndPersistShapeId(state0Shape, shapeIds);
            boolean allSame = true;

            for (int stateId = 0; stateId < states.size(); stateId++) {
                var blockState = states.get(stateId);

                var stateShape = new Shape(blockState);
                var stateShapeId = lookupAndPersistShapeId(stateShape, shapeIds);
                boxesByState[stateId] = stateShapeId;

                if (allSame && !Objects.equals(state0ShapeId, stateShapeId)) {
                    allSame = false;
                }
            }

            var blockId = getBlockIdString(block);
            if (allSame) {
                allBlocksJson.addProperty(blockId, state0ShapeId);
            } else {
                var blockJson = new JsonArray();
                for (int shapeId : boxesByState) {
                    blockJson.add(shapeId);
                }
                allBlocksJson.add(blockId, blockJson);

                System.err.println(blockId + ": " + boxesByState.length + " states, "
                    + (boxesByState.length - blockJson.size()) + " empty");
            }
        }

        var allShapesJson = new JsonObject();
        shapeIds.entrySet().stream().sorted(Map.Entry.comparingByValue())
            .forEach(entry -> allShapesJson.add(entry.getValue().toString(), entry.getKey().toJson()));

        var rootJson = new JsonObject();
        rootJson.add("blocks", allBlocksJson);
        rootJson.add("shapes", allShapesJson);
        return rootJson;
    }

    private static List<String> runSanityChecks(BlockCollisionBoxStorage storage) {
        var failures = new ArrayList<String>();
        for (var block : BuiltInRegistries.BLOCK) {
            var blockId = getBlockIdString(block);
            var states = block.getStateDefinition().getPossibleStates();
            for (int stateId = 0; stateId < states.size(); stateId++) {
                var blockState = states.get(stateId);

                var mcShape = new Shape(blockState);
                var storageShape = storage.getBlockShape(blockId, stateId);

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
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
    }

    // ATTRIBUTES
    public static JsonObject extractAttributes() {
        var allAttributes = new JsonObject();
        for (var attr : BuiltInRegistries.ATTRIBUTE) {
            var attrJson = new JsonObject();
            attrJson.addProperty("default", attr.getDefaultValue());
            if (attr instanceof RangedAttribute rangedAttr) {
                attrJson.addProperty("min", rangedAttr.getMinValue());
                attrJson.addProperty("max", rangedAttr.getMaxValue());
            }

            var key = BuiltInRegistries.ATTRIBUTE.getKey(attr);
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

    @SuppressWarnings("unchecked")
    private static Optional<BlockBehaviour.OffsetFunction> getOffsetFunction(BlockState block) {
        try {
            Field offsetFunctionField = BlockBehaviour.BlockStateBase.class.getDeclaredField("offsetFunction");
            offsetFunctionField.setAccessible(true);

            return (Optional<BlockBehaviour.OffsetFunction>) offsetFunctionField.get(block);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // BLOCKS
    private static JsonObject extractBlockProperties() {
        var rootJson = new JsonObject();

        var sawZeroYOffset = false;
        var sawNonZeroYOffset = false;
        for (var block : BuiltInRegistries.BLOCK) {
            var blockData = new JsonObject();

            blockData.addProperty("maxHorizontalOffset", block.getMaxHorizontalOffset());
            blockData.addProperty("maxVerticalOffset", block.getMaxVerticalOffset());

            // Blocks share properties with all states
            var defaultState = block.defaultBlockState();
            String offsetType = "NONE";
            if (defaultState.hasOffsetFunction()) {
                var offsetFunction = getOffsetFunction(defaultState).orElseThrow();
                var vec = offsetFunction.evaluate(Blocks.GRASS.defaultBlockState(), EmptyBlockGetter.INSTANCE, new BlockPos(1, 2, 3));

                if (vec.y == 0.0) {
                    sawZeroYOffset = true;
                    offsetType = "XZ";
                } else {
                    sawNonZeroYOffset = true;
                    offsetType = "XYZ";
                }
            }

            blockData.addProperty("offsetType", offsetType);
            blockData.addProperty("replaceable", defaultState.canBeReplaced());

            rootJson.add(getBlockIdString(block), blockData);
        }

        // Some validation to make sure we're not missing anything
        if (!sawZeroYOffset || !sawNonZeroYOffset) {
            throw new IllegalStateException("Didn't see both zero and non-zero Y offsets");
        }

        return rootJson;
    }
}
