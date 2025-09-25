/*
 * This file is part of Dynamic Surroundings, licensed under the MIT License (MIT).
 *
 * Copyright (c) OreCruncher
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.orecruncher.dsurround.client.handlers.effects;

import java.util.List;

import javax.annotation.Nonnull;

import org.orecruncher.dsurround.ModOptions;
import org.orecruncher.dsurround.client.effects.EntityEffect;
import org.orecruncher.dsurround.client.effects.IEntityEffectFactory;
import org.orecruncher.dsurround.client.effects.IEntityEffectFactoryFilter;
import org.orecruncher.dsurround.client.effects.IEntityEffectHandlerState;
import org.orecruncher.dsurround.client.fx.particle.ParticleTextPopOff;
import org.orecruncher.dsurround.client.handlers.EnvironStateHandler.EnvironState;
import org.orecruncher.dsurround.registry.effect.EntityEffectInfo;
import org.orecruncher.lib.Color;
import org.orecruncher.lib.Translations;
import org.orecruncher.lib.math.MathStuff;
import org.orecruncher.lib.random.XorShiftRandom;

import com.google.common.collect.ImmutableList;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class EntityHealthPopoffEffect extends EntityEffect {

	private static final Color CRITICAL_TEXT_COLOR = Color.MC_GOLD;
	private static final Color HEAL_TEXT_COLOR = Color.MC_GREEN;
	private static final Color DAMAGE_TEXT_COLOR = Color.MC_RED;

	private static final Translations xlate = new Translations();
	private static final int CRITWORD_COUNT = 85;
	private static final String CRITWORD_PREFIX = "critword.";

	static {
		xlate.load("/assets/dsurround/dsurround/data/critwords/");
	}

	protected float lastHealth;

	private static java.util.Map<String, Color> damageColorAliasMap;

	private static void ensureAliasMapInitialized() {
		if (damageColorAliasMap != null)
			return;
		final java.util.Map<String, Color> map = new java.util.HashMap<>();
		for (final String entry : ModOptions.effects.damageColorAliases) {
			if (entry == null)
				continue;
			final String trimmed = entry.trim();
			if (trimmed.isEmpty())
				continue;
			final int idx = trimmed.indexOf('=');
			if (idx <= 0 || idx >= trimmed.length() - 1)
				continue;
			final String alias = trimmed.substring(0, idx).trim().toLowerCase();
			final String colorKey = trimmed.substring(idx + 1).trim();
			final Color c = resolveColorByKey(colorKey);
			if (c != null)
				map.put(alias, c);
		}
		damageColorAliasMap = map;
	}

	private static Color resolveColorByKey(@Nonnull final String key) {
		// Try static fields on Color (e.g., MC_RED)
		try {
			final java.lang.reflect.Field f = Color.class.getField(key);
			final Object v = f.get(null);
			if (v instanceof Color)
				return (Color) v;
		} catch (final Throwable t) {
			// ignore and try other forms
		}
		// Try common names to MC_* constants
		final String k = key.toLowerCase();
		switch (k) {
			case "red":
			case "mc_red":
				return Color.MC_RED;
			case "green":
			case "mc_green":
				return Color.MC_GREEN;
			case "gold":
			case "yellow":
			case "mc_gold":
				return Color.MC_GOLD;
			case "white":
				return Color.WHITE;
			case "black":
				return Color.BLACK;
			case "blue":
				return Color.BLUE;
			case "magenta":
				return Color.MAGENTA;
		}
		// Try #RRGGBB or 0xRRGGBB
		try {
			String s = key;
			if (s.startsWith("#"))
				s = s.substring(1);
			if (s.startsWith("0x") || s.startsWith("0X"))
				s = s.substring(2);
			final int rgb = (int) Long.parseLong(s, 16);
			return new Color(rgb);
		} catch (final Throwable t) {
			return null;
		}
	}

	private static Color resolveStyleColorForDamage(@Nonnull final EntityLivingBase entity) {
		// Default damage color
		Color result = DAMAGE_TEXT_COLOR;
		final String tagName = ModOptions.effects.damageColorTagName;
		if (tagName == null || tagName.isEmpty())
			return result;
		final NBTTagCompound nbt = entity.getEntityData();
		if (nbt == null)
			return result;
		final String raw = nbt.getString(tagName);
		if (raw == null || raw.isEmpty())
			return result;
		ensureAliasMapInitialized();
		final Color mapped = damageColorAliasMap.get(raw.toLowerCase());
		return mapped != null ? mapped : result;
	}

	private static org.orecruncher.dsurround.client.fx.particle.ParticleTextPopOff.Style resolveStyle() {
		switch (ModOptions.effects.popoffTextStyle) {
			case 1:
				return org.orecruncher.dsurround.client.fx.particle.ParticleTextPopOff.Style.FLOAT_FADE;
			case 2:
				return org.orecruncher.dsurround.client.fx.particle.ParticleTextPopOff.Style.BOUNCE_STRONG;
			default:
				return org.orecruncher.dsurround.client.fx.particle.ParticleTextPopOff.Style.GROW_SHRINK;
		}
	}

	private String getPowerWord() {
		final int id = XorShiftRandom.current().nextInt(CRITWORD_COUNT);
		return xlate.loadString(CRITWORD_PREFIX + id) + "!";
	}

	@Override
	public void initialize(@Nonnull final IEntityEffectHandlerState state) {
		super.initialize(state);
		getState().subject().ifPresent(e -> this.lastHealth = ((EntityLivingBase) e).getHealth());
	}

	@Nonnull
	@Override
	public String name() {
		return "Health Tracker";
	}

	@Override
	public boolean receiveLastCall() {
		return true;
	}

	@Override
	public void update(@Nonnull final Entity subject) {
		if (!ModOptions.effects.enableDamagePopoffs)
			return;

		final EntityLivingBase entity = (EntityLivingBase) subject;
		if (this.lastHealth != entity.getHealth()) {
			final int adjustment = MathHelper.ceil(entity.getHealth() - this.lastHealth);

			this.lastHealth = entity.getHealth();

			// Don't display if it is the current player in first person view
			if (!EnvironState.isPlayer(subject) || !isFirstPersonView()) {

				final int delta = Math.max(1, MathStuff.abs(adjustment));
				final int criticalAmount = (int) (entity.getMaxHealth() / 2.5F);

				final AxisAlignedBB bb = entity.getEntityBoundingBox();
				double posX = entity.posX;
				double posY = bb.maxY + 0.5D;
				double posZ = entity.posZ;
				// 统一：始终在头顶正上方，按头部朝向的左右法向做轻微扰动，减少重叠
				final float yaw = entity.rotationYawHead;
				final double rad = Math.toRadians(yaw + 90.0F);
				final double lx = Math.cos(rad);
				final double lz = Math.sin(rad);
				final double jitter = (XorShiftRandom.current().nextFloat() - 0.5D) * 0.3D;
				posX += lx * jitter;
				posZ += lz * jitter;
				final String text = String.valueOf(delta);
				final Color color = adjustment > 0 ? HEAL_TEXT_COLOR : resolveStyleColorForDamage(entity);

				final World world = EnvironState.getWorld();

				ParticleTextPopOff particle;
				if (ModOptions.effects.showCritWords && adjustment < 0 && delta >= criticalAmount) {
					particle = new ParticleTextPopOff(world, getPowerWord(), CRITICAL_TEXT_COLOR, posX, posY + 0.5D,
							posZ).setStyle(resolveStyle());
					getState().addParticle(particle);
				}
				particle = new ParticleTextPopOff(world, text, color, posX, posY, posZ).setStyle(resolveStyle());
				getState().addParticle(particle);
			}
		}
	}

	// Currently restricted to the active player. Have stuff to unwind in the
	// footprint code.
	public static final IEntityEffectFactoryFilter DEFAULT_FILTER = (@Nonnull final Entity e,
			@Nonnull final EntityEffectInfo eei) -> e instanceof EntityLivingBase;

	public static class Factory implements IEntityEffectFactory {

		@Nonnull
		@Override
		public List<EntityEffect> create(@Nonnull final Entity entity) {
			return ImmutableList.of(new EntityHealthPopoffEffect());
		}
	}

}
