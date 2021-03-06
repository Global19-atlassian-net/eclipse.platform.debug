/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.externaltools.internal.model;


/**
 * Constants used to identify user preferences.
 */
public interface IPreferenceConstants {

	/**
	 * Boolean preference key which indicates whether or not the user should be prompted
	 * before an external tool project builder is migrated to the new builder format.
	 * This is used before an old-style (Eclipse 1.0 or 2.0) builder is migrated to
	 * the new format (launch configurations).
	 */
	String PROMPT_FOR_TOOL_MIGRATION = "externaltools.builders.promptForMigration"; //$NON-NLS-1$
	/**
	 * Boolean preference key which indicates whether or not the user should be prompted
	 * before a project is migrated tot he new builder handle format.
	 * This is used before an old-style (Eclipse 2.1) project handle is migrated
	 * from the old format (launch config handles) to the new format (path to the launch).
	 */
	String PROMPT_FOR_PROJECT_MIGRATION = "externaltools.builders.promptForProjectMigration"; //$NON-NLS-1$
}
