package eu.pb4.brewery.drink;

import eu.pb4.brewery.BreweryInit;
import eu.pb4.brewery.item.BrewItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DrinkUtils {
    public static final String TYPE_NBT = "BrewType";
    public static final String QUALITY_NBT = "BrewQuality";
    public static final String AGE_NBT = "BrewAge";
    public static final String AGE_COOK_NBT = "BrewCookAge";
    public static final String QUALITY_MULT_NBT = "BrewQualityMult";
    public static final String BARREL_TYPE_NBT = "BrewBarrelType";
    public static final String DISTILLATED_NBT = "BrewDistillated";
    public static final String HEAT_SOURCE_NBT = "BrewHeatSource";

    @Nullable
    public static DrinkType getType(ItemStack stack) {
        if (stack.isOf(BrewItems.DRINK_ITEM) && stack.hasNbt()) {
            var id = Identifier.tryParse(stack.getNbt().getString(TYPE_NBT));

            var type = BreweryInit.DRINK_TYPES.get(id);

            if (type != null) {
                return type;
            } else if (id.getPath().startsWith("drinks/")) {
                // Bug in 0.1.0 caused drinks to have wrong ids assigned (didn't cause functionality loss). So here is the patch
                // to handle these old drinks.
                var id2 = new Identifier(id.getNamespace(), id.getPath().substring("drinks/".length()));
                type = BreweryInit.DRINK_TYPES.get(id2);

                return type;
            }
        }

        return null;
    }

    public static double getQuality(ItemStack stack) {
        if (stack.isOf(BrewItems.DRINK_ITEM) && stack.hasNbt()) {
            return stack.getNbt().getDouble(QUALITY_NBT);
        }

        return -1;
    }

    public static String getBarrelType(ItemStack stack) {
        if (stack.isOf(BrewItems.DRINK_ITEM) && stack.hasNbt()) {
            return stack.getNbt().getString(BARREL_TYPE_NBT);
        }

        return "";
    }

    public static boolean getDistillationStatus(ItemStack stack) {
        if (stack.hasNbt()) {
            var type = DrinkUtils.getType(stack);
            if (type != null) {
                return stack.getNbt().getInt(DISTILLATED_NBT) >= type.distillationRuns();
            }
        }

        return false;
    }

    @Nullable
    public static Block getHeatSource(ItemStack stack) {
        if (stack.hasNbt() && stack.getNbt().contains(HEAT_SOURCE_NBT)) {
            return Registries.BLOCK.get(Identifier.tryParse(stack.getNbt().getString(HEAT_SOURCE_NBT)));
        }

        return Blocks.FIRE;
    }

    public static boolean canBeDistillated(ItemStack stack) {
        var type = DrinkUtils.getType(stack);
        var already = DrinkUtils.getDistillationStatus(stack);
        return !already && ((type != null && type.requireDistillation()) || stack.isOf(BrewItems.INGREDIENT_MIXTURE));
    }

    public static double getAgeInTicks(ItemStack stack) {
        return getAgeInTicks(stack, Double.MIN_VALUE);
    }

    public static double getAgeInTicks(ItemStack stack, double defaultValue) {
        if (stack.hasNbt() && stack.getNbt().contains(AGE_NBT, NbtElement.NUMBER_TYPE)) {
            return stack.getNbt().getDouble(AGE_NBT);
        }

        return defaultValue;
    }

    public static double getAgeInSeconds(ItemStack stack) {
        return getAgeInTicks(stack) / 20d;
    }

    public static ItemStack createDrink(Identifier type, int age, double quality, int distillated, Identifier heatingSource) {
        var stack = new ItemStack(BrewItems.DRINK_ITEM);

        stack.getOrCreateNbt().putInt(AGE_NBT, age);
        stack.getOrCreateNbt().putDouble(QUALITY_NBT, quality);
        stack.getOrCreateNbt().putString(TYPE_NBT, type.toString());
        stack.getOrCreateNbt().putInt(DISTILLATED_NBT, distillated);
        stack.getOrCreateNbt().putString(HEAT_SOURCE_NBT, heatingSource.toString());

        return stack;
    }

    public static ItemStack createDrink(Identifier type, double qualityMultiplier) {
        var stack = new ItemStack(BrewItems.DRINK_ITEM);

        stack.getOrCreateNbt().putInt(AGE_NBT, 0);
        stack.getOrCreateNbt().putString(TYPE_NBT, type.toString());
        stack.getOrCreateNbt().putDouble(QUALITY_MULT_NBT, qualityMultiplier);

        return stack;
    }

    public static double getIngredientMultiplier(ItemStack stack) {
        if (stack.hasNbt() && stack.getNbt().contains(QUALITY_MULT_NBT, NbtElement.NUMBER_TYPE)) {
            return stack.getNbt().getDouble(QUALITY_MULT_NBT);
        }

        return 1;
    }

    public static List<DrinkType> findTypes(List<ItemStack> ingredients, Identifier barrelType, Block heatSource) {
        if (ingredients.isEmpty()) {
            return List.of();
        }
        var list = new ArrayList<DrinkType>();
        base:
        for (var type : BreweryInit.DRINK_TYPES.values()) {
            if (((barrelType == null && type.barrelInfo().isEmpty()) || (barrelType != null && type.getBarrelInfo(barrelType) != null))
                    && !type.ingredients().isEmpty() && (type.heatSource().isEmpty() || type.heatSource().get().contains(Registries.BLOCK.getEntry(heatSource)))) {
                var ing = new ArrayList<ItemStack>(ingredients.size());
                for (var i : ingredients) {
                    ing.add(new ItemStack(i.getItem(), i.getCount()));
                }

                for (var ingredient : type.ingredients()) {
                    int count = ingredient.count();
                    for (var stack : ing) {
                        if (!stack.isEmpty() && ingredient.items().contains(stack.getItem())) {
                            count -= stack.getCount();
                            if (count < 0) {
                                continue base;
                            } else {
                                stack.setCount(0);
                            }
                        }
                    }

                    if (count != 0) {
                        continue base;
                    }
                }
                list.add(type);
            }
        }

        return list;
    }
}
