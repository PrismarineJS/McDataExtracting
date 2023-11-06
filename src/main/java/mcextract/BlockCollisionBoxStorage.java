package mcextract;

import com.google.gson.JsonObject;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BlockCollisionBoxStorage {
    private final Map<String, ShapePerBlockState> blocks = new HashMap<>();

    public BlockCollisionBoxStorage(JsonObject jsonRoot) {
        var shapes = new HashMap<Integer, Shape>();
        for (var shapeEntry : jsonRoot.getAsJsonObject("shapes").entrySet()) {
            var boxesJson = shapeEntry.getValue().getAsJsonArray();
            var boxes = new ArrayList<AABB>(boxesJson.size());
            for (var element : boxesJson) {
                var a = element.getAsJsonArray();
                int i = 0;
                boxes.add(new AABB(
                        a.get(i++).getAsDouble(),
                        a.get(i++).getAsDouble(),
                        a.get(i++).getAsDouble(),
                        a.get(i++).getAsDouble(),
                        a.get(i++).getAsDouble(),
                        a.get(i++).getAsDouble()));
            }
            shapes.put(Integer.parseInt(shapeEntry.getKey()), new Shape(boxes));
        }

        for (var blockEntry : jsonRoot.getAsJsonObject("blocks").entrySet()) {
            var value = blockEntry.getValue();
            if (value == null) {
                blocks.put(blockEntry.getKey(), new SameShapePerBlockState(Shape.EMPTY));
            } else if (value.isJsonPrimitive()) {
                var shape = shapes.get(value.getAsInt());
                blocks.put(blockEntry.getKey(), new SameShapePerBlockState(shape));
            } else {
                var shapeIds = value.getAsJsonArray();
                var blockStatesShapes = new Shape[shapeIds.size()];
                for (int i = 0; i < blockStatesShapes.length; i++) {
                    blockStatesShapes[i] = shapes.get(shapeIds.get(i).getAsInt());
                }
                blocks.put(blockEntry.getKey(), new OneShapePerBlockState(blockStatesShapes));
            }
        }
    }

    public Shape getBlockShape(String blockId, int blockStateId) {
        final ShapePerBlockState blockStatesShapeIds = blocks.get(blockId);
        if (blockStatesShapeIds == null) return null; // unknown block
        return blockStatesShapeIds.getShapeIdForBlockState(blockStateId);
    }

    private interface ShapePerBlockState {
        Shape getShapeIdForBlockState(int blockStateId);
    }

    private record SameShapePerBlockState(Shape shape) implements ShapePerBlockState {
        @Override
        public Shape getShapeIdForBlockState(int blockStateId) {
            return shape;
        }
    }

    private record OneShapePerBlockState(Shape[] shapes) implements ShapePerBlockState {
        @Override
        public Shape getShapeIdForBlockState(int blockStateId) {
            if (blockStateId < 0) throw new IllegalArgumentException("block state id < 0");
            if (blockStateId >= shapes.length) {
                throw new IllegalArgumentException("block state id " + blockStateId
                        + " out of bounds for length " + shapes.length);
            }
            if (shapes[blockStateId] == null) return Shape.EMPTY;
            return shapes[blockStateId];
        }
    }
}
