package proto.mechanicalarmory.client.flywheel.gltf;

import dev.engine_room.flywheel.api.model.Model;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;

public final class MyModelTree {
	@Nullable
	private final Model model;
	private final MyPartPose initialPose;
	private final MyModelTree[] children;
	private final String[] childNames;

	/**
	 * Create a new ModelTree node.
	 *
	 * @param model       The model to associate with this node, or null if this node does not render.
	 * @param initialPose The initial pose of this node.
	 * @param children    The children of this node.
	 */
	public MyModelTree(@Nullable Model model, MyPartPose initialPose, Map<String, MyModelTree> children) {
		this.model = model;
		this.initialPose = initialPose;

		String[] childNames = children.keySet().toArray(String[]::new);
		Arrays.sort(childNames);

		MyModelTree[] childArray = new MyModelTree[childNames.length];
		for (int i = 0; i < childNames.length; i++) {
			childArray[i] = children.get(childNames[i]);
		}

		this.children = childArray;
		this.childNames = childNames;
	}

	@Nullable
	public Model model() {
		return model;
	}

	public MyPartPose initialPose() {
		return initialPose;
	}

	public int childCount() {
		return children.length;
	}

	public MyModelTree child(int index) {
		return children[index];
	}

	public String childName(int index) {
		return childNames[index];
	}

	public int childIndex(String name) {
		return Arrays.binarySearch(childNames, name);
	}

	public boolean hasChild(String name) {
		return childIndex(name) >= 0;
	}

	@Nullable
	public MyModelTree child(String name) {
		int index = childIndex(name);

		if (index < 0) {
			return null;
		}

		return child(index);
	}

	public MyModelTree childOrThrow(String name) {
		MyModelTree child = child(name);

		if (child == null) {
			throw new NoSuchElementException("Can't find part " + name);
		}

		return child;
	}
}
