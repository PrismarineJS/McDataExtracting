package mcextract;

import com.google.gson.*;
import net.minecraft.util.math.Box;

import java.util.*;

public class BlockCollisionBoxStorage {
	private final HashMap<String, ShapePerBlockState> blocks = new HashMap<>();

	public BlockCollisionBoxStorage(JsonObject jsonRoot) {
		final HashMap<Integer, Shape> shapes = new HashMap<>();
		for (Map.Entry<String, JsonElement> shapeEntry : jsonRoot.getAsJsonObject("shapes").entrySet()) {
			final JsonArray boxesJson = shapeEntry.getValue().getAsJsonArray();
			final ArrayList<Box> boxes = new ArrayList<>(boxesJson.size());
			for (JsonElement element : boxesJson) {
				final JsonArray a = element.getAsJsonArray();
				int i = 0;
				boxes.add(new Box(
						a.get(i++).getAsDouble(),
						a.get(i++).getAsDouble(),
						a.get(i++).getAsDouble(),
						a.get(i++).getAsDouble(),
						a.get(i++).getAsDouble(),
						a.get(i++).getAsDouble()));
			}
			shapes.put(Integer.parseInt(shapeEntry.getKey()), new Shape(boxes));
		}

		for (Map.Entry<String, JsonElement> blockEntry : jsonRoot.getAsJsonObject("blocks").entrySet()) {
			final JsonElement value = blockEntry.getValue();
			if (value == null) {
				blocks.put(blockEntry.getKey(), new SameShapePerBlockState(Shape.EMPTY));
			} else if (value.isJsonPrimitive()) {
				final Shape shape = shapes.get(value.getAsInt());
				blocks.put(blockEntry.getKey(), new SameShapePerBlockState(shape));
			} else {
				final JsonObject shapeIdsByBlockStateId = value.getAsJsonObject();
				final Integer maxStateId = shapeIdsByBlockStateId.entrySet().stream()
						.map(e -> Integer.parseInt(e.getKey()))
						.max(Comparator.naturalOrder()).orElse(0);
				final Shape[] blockStatesShapes = new Shape[maxStateId + 1];
				for (Map.Entry<String, JsonElement> bsEntry : shapeIdsByBlockStateId.entrySet()) {
					final Shape shape = shapes.get(bsEntry.getValue().getAsInt());
					blockStatesShapes[Integer.parseInt(bsEntry.getKey())] = shape;
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

	private static class SameShapePerBlockState implements ShapePerBlockState {
		private final Shape shape;

		private SameShapePerBlockState(Shape shape) {
			this.shape = shape;
		}

		@Override
		public Shape getShapeIdForBlockState(int blockStateId) {
			return shape;
		}
	}

	private static class OneShapePerBlockState implements ShapePerBlockState {
		private final Shape[] shapes;

		private OneShapePerBlockState(Shape[] shapes) {
			this.shapes = shapes;
		}

		@Override
		public Shape getShapeIdForBlockState(int blockStateId) {
			if (blockStateId < 0) throw new IllegalArgumentException("block state id < 0");
			if (blockStateId >= shapes.length) return Shape.EMPTY;
			if (shapes[blockStateId] == null) return Shape.EMPTY;
			return shapes[blockStateId];
		}
	}
}
