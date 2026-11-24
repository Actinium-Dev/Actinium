// Derived from Continuity-For-Actinium (4281538), PepperCode1 and contributors.
// SPDX-License-Identifier: LGPL-3.0-only
package com.dhj.actinium.resourcepack.ctm.client.processor;

import net.minecraft.util.EnumFacing;

public enum Symmetry {
	NONE,
	OPPOSITE,
	ALL;

	public EnumFacing apply(EnumFacing face) {
		if (this == Symmetry.OPPOSITE) {
			if (face.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE) {
				face = face.getOpposite();
			}
		} else if (this == Symmetry.ALL) {
			face = EnumFacing.DOWN;
		}
		return face;
	}
}
