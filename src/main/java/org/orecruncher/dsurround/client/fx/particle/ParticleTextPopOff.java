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

package org.orecruncher.dsurround.client.fx.particle;

import javax.annotation.Nonnull;

import org.orecruncher.lib.Color;
import org.orecruncher.lib.gfx.OpenGlState;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ParticleTextPopOff extends ParticleBase {

	protected static final float GRAVITY = 0.8F;
	protected static final float SIZE = 3.0F;
	protected static final int LIFESPAN = 12;
	protected static final double BOUNCE_STRENGTH = 1.5F;
	protected static final int SHADOW_COLOR = Color.BLACK.rgbWithAlpha(1F);

	protected int renderColor = Color.WHITE.rgbWithAlpha(1F);
	protected Color baseColor = Color.WHITE;
	protected boolean grow = true;

	protected String text;
	protected float drawX;
	protected float drawY;

	public enum Style {
		GROW_SHRINK,
		FLOAT_FADE,
		BOUNCE_STRONG
	}

	protected Style style = Style.GROW_SHRINK;

	public ParticleTextPopOff(final World world, final String text, final Color color, final double x, final double y,
			final double z) {
		this(world, text, color, x, y, z, 0.001D, 0.05D * BOUNCE_STRENGTH, 0.001D);
	}

	public ParticleTextPopOff(final World world, final String text, final Color color, final double x, final double y,
			final double z, final double dX, final double dY, final double dZ) {
		super(world, x, y, z, dX, dY, dZ);

		this.baseColor = color;
		this.renderColor = color.rgbWithAlpha(1F);
		this.motionX = dX;
		this.motionY = dY;
		this.motionZ = dZ;
		final float dist = MathHelper
				.sqrt(this.motionX * this.motionX + this.motionY * this.motionY + this.motionZ * this.motionZ);
		this.motionX = (this.motionX / dist * 0.12D);
		this.motionY = (this.motionY / dist * 0.12D);
		this.motionZ = (this.motionZ / dist * 0.12D);
		this.particleTextureJitterX = 1.5F;
		this.particleTextureJitterY = 1.5F;
		this.particleGravity = GRAVITY;
		this.particleScale = SIZE;
		this.particleMaxAge = LIFESPAN;

		setText(text);
	}

	public ParticleTextPopOff setStyle(@Nonnull final Style style) {
		this.style = style;
		return this;
	}

	public ParticleTextPopOff setText(@Nonnull final String text) {
		this.text = text;
		this.drawX = -MathHelper.floor(this.font.getStringWidth(this.text) / 2.0F) + 1;
		this.drawY = -MathHelper.floor(this.font.FONT_HEIGHT / 2.0F) + 1;
		return this;
	}

	public ParticleTextPopOff setColor(@Nonnull final Color color) {
		this.baseColor = color;
		this.renderColor = color.rgbWithAlpha(1F);
		return this;
	}

	@Override
	public void renderParticle(@Nonnull BufferBuilder worldRendererIn, @Nonnull Entity entityIn, float partialTicks, float rotationX,
							   float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {

		final float pitch = this.manager.playerViewX * (isThirdPersonView() ? -1 : 1);
		final float yaw = -this.manager.playerViewY;

		final float locX = ((float) (this.prevPosX + (this.posX - this.prevPosX) * partialTicks - interpX()));
		final float locY = ((float) (this.prevPosY + (this.posY - this.prevPosY) * partialTicks - interpY()));
		final float locZ = ((float) (this.prevPosZ + (this.posZ - this.prevPosZ) * partialTicks - interpZ()));

		final OpenGlState glState = OpenGlState.push();
		GlStateManager.translate(locX, locY, locZ);
		GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);
		GlStateManager.rotate(pitch, 1.0F, 0.0F, 0.0F);
		GlStateManager.scale(-1.0F, -1.0F, 1.0F);
		// 根据距离做缩放，使视觉大小基本保持一致
		final float distToCam = MathHelper.sqrt(locX * locX + locY * locY + locZ * locZ);
		final double targetDist = 16.0D; // 参考距离（可按需微调）
		final double distanceScale = MathHelper.clamp(distToCam / (float) targetDist, 1.0F, 4.0F);
		final double renderScale = this.particleScale * 0.008D * distanceScale;
		GlStateManager.scale(renderScale, renderScale, renderScale);

		// 禁用深度测试，确保绘制在所有实体和方块之上
		GlStateManager.disableDepth();
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 0.003662109F);
		this.font.drawString(this.text, this.drawX, this.drawY, SHADOW_COLOR, false);
		GlStateManager.translate(-0.3F, -0.3F, -0.001F);
		this.font.drawString(this.text, this.drawX, this.drawY, this.renderColor, false);
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, OpenGlHelper.lastBrightnessX,
				OpenGlHelper.lastBrightnessY);
		GlStateManager.enableDepth();
		OpenGlState.pop(glState);

		switch (this.style) {
			case GROW_SHRINK:
				if (this.grow) {
					this.particleScale *= 1.08F;
					if (this.particleScale > SIZE * 3.0D) {
						this.grow = false;
					}
				} else {
					this.particleScale *= 0.96F;
				}
				break;
				case FLOAT_FADE: {
					final int age = this.particleAge;
					final int max = this.particleMaxAge;
					final float t = max > 0 ? age / (float) max : 0F;
					// Phase 1: quicker pop-in (0% - 15%), lower vertical motion, no gravity
					if (t < 0.15F) {
						final float k = t / 0.15F; // 0 -> 1
						this.particleScale = SIZE * (1.0F + k * 1.0F); // up to ~2.0x，前期更快
						this.renderColor = this.baseColor.rgbWithAlpha(1F);
						// keep height low early, but ensure upward movement
						this.particleGravity = 0.0F;
						if (this.motionY < 0.004D) this.motionY = 0.004D;
					} else if (t < 0.7F) {
						// Phase 2: longer hold (15% - 70%), light gravity, slight upward drift
						this.particleScale = SIZE * 2.0F;
						this.renderColor = this.baseColor.rgbWithAlpha(1F);
						this.particleGravity = 0.1F;
						if (this.motionY < 0.002D) this.motionY = 0.002D;
					} else {
						// Phase 3: rise quickly, shrink and fade (70% - 100%)
						this.particleGravity = 0.3F;
						this.motionY += 0.02D;
						this.particleScale *= 0.94F;
						final float k = (t - 0.7F) / 0.3F; // 0 -> 1 over the last 30%
						final float a = Math.max(0.0F, 1.0F - k);
						this.renderColor = this.baseColor.rgbWithAlpha(a);
					}
					break;
				}
			case BOUNCE_STRONG:
				this.motionY += 0.012D;
				this.particleScale *= this.grow ? 1.12F : 0.94F;
				if (this.particleScale > SIZE * 3.5D) this.grow = false;
				break;
		}
	}

	@Override
	public boolean shouldDisableDepth() {
		return true;
	}

	@Override
	public int getFXLayer() {
		return 3;
	}
}
