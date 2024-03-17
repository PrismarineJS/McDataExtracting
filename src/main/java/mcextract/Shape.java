package mcextract;

import com.google.gson.JsonArray;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * The shape of a {@link BlockState}, composed of one or more axis-aligned AABBs.
 */
public class Shape {
    public static final Shape EMPTY = new Shape(Collections.emptyList());

    public final Collection<AABB> boxes;

    public Shape(Collection<AABB> boxes) {
        this.boxes = boxes;
    }

    public Shape(BlockState blockState) {
        var collisionShape = blockState.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        var offset = blockState.getOffset(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        var reversedOffset = offset.reverse();
        var collisionShapeWithoutOffset = collisionShape.move(reversedOffset.x, reversedOffset.y, reversedOffset.z);

        this.boxes = collisionShapeWithoutOffset.toAabbs();
    }

    public static JsonArray jsonArrayFromBox(AABB box) {
        final JsonArray boxJson = new JsonArray();
        boxJson.add(box.minX);
        boxJson.add(box.minY);
        boxJson.add(box.minZ);
        boxJson.add(box.maxX);
        boxJson.add(box.maxY);
        boxJson.add(box.maxZ);
        return boxJson;
    }

    public JsonArray toJson() {
        final JsonArray boxesJson = new JsonArray();
        for (AABB box : boxes) {
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
}
