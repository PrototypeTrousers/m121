package proto.mechanicalarmory.common.recipes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecipeRegistry {
    public static final RecipeRegistry INSTANCE = new RecipeRegistry();
    private final Map<String, List<Recipe>> recipes = new HashMap<>();

    private RecipeRegistry() {}

    public static RecipeRegistry getInstance() {
        return INSTANCE;
    }

    public List<Recipe> register(String key) {
        List<Recipe> l = new ArrayList<>();
        recipes.put(key, l);
        return l;
    }

    public List<Recipe> getRecipes(String key) {
        return recipes.get(key);
    }
}
