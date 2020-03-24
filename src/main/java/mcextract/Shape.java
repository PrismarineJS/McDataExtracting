package mcextract;

import com.google.gson.JsonArray;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * The shape of a {@link IBlockState}, composed of one or more axis-aligned {@link AxisAlignedBB}es.
 */
public class Shape {
	public static final Shape EMPTY = new Shape(Collections.emptyList());
	private static final boolean isActualState = true;
	private static final AxisAlignedBB entityBox = new AxisAlignedBB(
			0.0D, 0.0D, 0.0D, 1.0D, 2.0D, 1.0D);

	public final List<AxisAlignedBB> boxes;

	public Shape(List<AxisAlignedBB> boxes) {
		this.boxes = boxes;
	}

	public Shape(IBlockState blockState, World world) {
		boxes = new ArrayList<>();
		world.setBlockState(BlockPos.ORIGIN, blockState);
		blockState.addCollisionBoxToList(world, BlockPos.ORIGIN, entityBox, boxes, null, isActualState);
	}

	public JsonArray toJson() {
		final JsonArray boxesJson = new JsonArray();
		for (AxisAlignedBB box : boxes) {
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

	public static JsonArray jsonArrayFromBox(AxisAlignedBB box) {
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
