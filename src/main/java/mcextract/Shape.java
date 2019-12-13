package mcextract;

import com.google.gson.JsonArray;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.EmptyBlockView;

import java.util.*;

/**
 * The shape of a {@link BlockState}, composed of one or more axis-aligned {@link Box}es.
 */
public class Shape {
	public static final Shape EMPTY = new Shape(Collections.emptyList());

	public final Collection<Box> boxes;

	public Shape(Collection<Box> boxes) {
		this.boxes = boxes;
	}

	public Shape(BlockState blockState) {
		this(blockState.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).getBoundingBoxes());
	}

	public JsonArray toJson() {
		final JsonArray boxesJson = new JsonArray();
		for (Box box : boxes) {
			boxesJson.add(jsonArrayFromBox(box));
		}
		return boxesJson;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Shape shape = (Shape) o;
		return Objects.equals(boxes, shape.boxes);
	}

	@Override
	public int hashCode() {
		return Objects.hash(boxes);
	}

	public static JsonArray jsonArrayFromBox(Box box) {
		final JsonArray boxJson = new JsonArray();
		boxJson.add(box.minX);
		boxJson.add(box.minY);
		boxJson.add(box.minZ);
		boxJson.add(box.maxX);
		boxJson.add(box.maxY);
		boxJson.add(box.maxZ);
		return boxJson;
	}
}
