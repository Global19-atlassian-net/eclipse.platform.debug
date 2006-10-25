/*******************************************************************************
 * Copyright (c) 2005, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.contexts.provisional;

import org.eclipse.debug.core.ILaunch;

/**
 * Listeners are notified when a launch has suspended at a context
 * where debugging should being. For example, at a breakpoint.
 * <p>
 * Clients may implement this interface.
 * </p>
 * @since 3.3
 * <p>
 * <strong>EXPERIMENTAL</strong>. This interface has been added as
 * part of a work in progress. There is no guarantee that this API will
 * remain unchanged during the 3.3 release cycle. Please do not use this API
 * without consulting with the Platform/Debug team.
 * </p>
 */
public interface ISuspendTriggerListener {
	
	/**
	 * Notification the given launch has suspended at the
	 * specified context.
	 * 
	 * @param launch
	 * @param context
	 */
	public void suspended(ILaunch launch, Object context);

}
