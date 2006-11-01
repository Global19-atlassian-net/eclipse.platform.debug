/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     QNX Software Systems - Mikhail Khodjaiants - Registers View (Bug 53640)
 *******************************************************************************/
package org.eclipse.debug.internal.ui.preferences;


import org.eclipse.debug.ui.IDebugUIConstants;
 
/**
 * Constants defining the keys to be used for accessing preferences
 * inside the debug ui plugin's preference bundle.
 *
 * In descriptions (of the keys) below describe the preference 
 * stored at the given key. The type indicates type of the stored preferences
 *
 * The preference store is loaded by the plugin (DebugUIPlugin).
 * @see DebugUIPlugin.initializeDefaultPreferences(IPreferenceStore) - for initialization of the store
 */
public interface IDebugPreferenceConstants {

	/**
	 * The symbolic names for colors for displaying the content in the Console
	 * @see org.eclipse.jface.resource.ColorRegistry
	 */
	public static final String CONSOLE_SYS_ERR_COLOR= "org.eclipse.debug.ui.errorColor"; //$NON-NLS-1$
	public static final String CONSOLE_SYS_OUT_COLOR= "org.eclipse.debug.ui.outColor"; //$NON-NLS-1$
	public static final String CONSOLE_SYS_IN_COLOR= "org.eclipse.debug.ui.inColor"; //$NON-NLS-1$
	
	/**
	 * @deprecated use IDebugUIConstants.PREF_MEMORY_HISTORY_UNKNOWN_COLOR instead
	 */
	public static final String MEMORY_VIEW_UNBUFFERED_LINE_COLOR = IDebugUIConstants.PLUGIN_ID + ".MemoryViewLineColor"; //$NON-NLS-1$
	
	/**
	 * @deprecated use IDebugUIConstants.PREF_MEMORY_HISTORY_KNOWN_COLOR instead
	 */
	public static final String MEMORY_VIEW_BUFFERED_LINE_COLOR = IDebugUIConstants.PLUGIN_ID + ".MemoryViewBufferedLineColor"; //$NON-NLS-1$
	
	/**
	 * (boolean) Whether or not the text in the console will wrap
	 */
	public static final String CONSOLE_WRAP= "Console.wrap"; //$NON-NLS-1$
	
	/**
	 * (int) The maximum console character width, if wrapping. 
	 */ 
	public static final String CONSOLE_WIDTH = "Console.width"; //$NON-NLS-1$
	
	/**
	 * (boolean) Whether or not the console view is shown 
	 * when there is program output.
  	 */
	public static final String CONSOLE_OPEN_ON_OUT= "DEBUG.consoleOpenOnOut"; //$NON-NLS-1$
	/**
	 * (boolean) Whether or not the console view is shown 
	 * when there is program error.
  	 */
	public static final String CONSOLE_OPEN_ON_ERR= "DEBUG.consoleOpenOnErr"; //$NON-NLS-1$
	
	/**
	 * Console buffer high and low water marks
	 */
	public static final String CONSOLE_LIMIT_CONSOLE_OUTPUT = "Console.limitConsoleOutput"; //$NON-NLS-1$
	public static final String CONSOLE_LOW_WATER_MARK = "Console.lowWaterMark"; //$NON-NLS-1$ 
	public static final String CONSOLE_HIGH_WATER_MARK = "Console.highWaterMark"; //$NON-NLS-1$
	
	/**
	 * Integer preference specifying the number of spaces composing a
	 * tab in the console.
	 * 
	 * @since 3.0
	 */
	public static final String CONSOLE_TAB_WIDTH= "Console.console_tab_width"; //$NON-NLS-1$
	
	/**
	 * Font preference setting for the process console.
	 * 
	 * @since 3.1
	 */
	public static final String CONSOLE_FONT= "org.eclipse.debug.ui.consoleFont"; //$NON-NLS-1$
	
	/**
	 * The orientation of the detail view in the VariablesView
	 */
	public static final String VARIABLES_DETAIL_PANE_ORIENTATION = "Variables.detail.orientation"; //$NON-NLS-1$
	public static final String EXPRESSIONS_DETAIL_PANE_ORIENTATION = "Expressions.detail.orientation"; //$NON-NLS-1$
	public static final String REGISTERS_DETAIL_PANE_ORIENTATION = "Registers.detail.orientation"; //$NON-NLS-1$
	public static final String VARIABLES_DETAIL_PANE_RIGHT = "Variables.detail.orientation.right"; //$NON-NLS-1$
	public static final String VARIABLES_DETAIL_PANE_UNDERNEATH = "Variables.detail.orientation.underneath"; //$NON-NLS-1$
	public static final String VARIABLES_DETAIL_PANE_HIDDEN = "Variables.detail.orientation.hidden"; //$NON-NLS-1$
	
	/**
	 * Memento for the last selected launch config in the
	 * launch config dialog.
	 * 
	 * @deprecated no longer supported
	 */
	public static final String PREF_LAST_LAUNCH_CONFIGURATION_SELECTION = IDebugUIConstants.PLUGIN_ID + ".lastLaunchConfigSelection"; //$NON-NLS-1$
		
	/**
	 * The maximum size of the launch history list
	 */
	public static int MAX_LAUNCH_HISTORY_SIZE= 20;

	/**
	 * Boolean preference controlling whether the text in the detail panes is
	 * wrapped. When <code>true</code> the text in the detail panes will be
	 * wrapped in new variable view.
	 *
	 * @since 2.1
	 */
	public static final String PREF_DETAIL_PANE_WORD_WRAP = IDebugUIConstants.PLUGIN_ID + ".detail_pane_word_wrap"; //$NON-NLS-1$
	
	/**
	 * Column size preference for the Memory View
	 * 
	 * @since 3.0
	 */
	public static final String PREF_COLUMN_SIZE = "org.eclipse.debug.ui.memory.columnSize"; //$NON-NLS-1$
	
	/**
	 * Default column size for the Memory View
	 * 
	 * @since 3.0
	 */
	public static final int PREF_COLUMN_SIZE_DEFAULT = 4;
	
	
	/**
	 * Row size preference for Memory View
	 * 
	 * @since 3.2
	 */
	public static final String PREF_ROW_SIZE = "org.eclipse.debug.ui.memory.rowSize"; //$NON-NLS-1$
	
	/**
	 * Default row size for the Memory View
	 * 
	 * @since 3.2
	 */
	public static final int PREF_ROW_SIZE_DEFAULT = 16;
	
	/**
	 * Stores the boolean preference of whether to prompt when removing all breakpoints.
	 * @since 3.3
	 */
	public static final String PREF_PROMPT_REMOVE_ALL_BREAKPOINTS = IDebugUIConstants.PLUGIN_ID + ".remove_all_breakpoints_prompt"; //$NON-NLS-1$
		
	/**
	 * stroes the boolean preference of whether or not to promtp wehen removing all of the breakpopints form a breakpoints
	 * container.
	 * @since 3.3 
	 */
	public static final String PREF_PROMPT_REMOVE_BREAKPOINTS_FROM_CONTAINER = IDebugUIConstants.PLUGIN_ID + ".remove_breakpoints_from_container_prompt"; //$NON-NLS-1$
	
	/**
	 * Default padded string for renderings
	 * 
	 * @since 3.1
	 */
	public static final String PREF_PADDED_STR_DEFAULT = "??"; //$NON-NLS-1$
	
	/**
	 * Default ASCII code page if ASCII code page preference is not set.
	 * @since 3.1
	 */
	public static final String DEFAULT_ASCII_CP = "CP1252"; //$NON-NLS-1$
	
	
	/**
	 * Default EBCDIC code page if EBCDIC code page preference is not set.
	 * @since 3.1
	 */
	public static final String DEFAULT_EBCDIC_CP = "CP037"; //$NON-NLS-1$
	
	/**
	 * Preference to determine if table rendering should dynamically load
	 * memory as the user scrolls
	 * 
	 * @since 3.1
	 */
	public static final String PREF_DYNAMIC_LOAD_MEM = "org.eclpise.debug.ui.memory.dynamicLoad"; //$NON-NLS-1$
	
	
	/**
	 * Size of buffer in a table rendering when dynamic loading mode is off.
	 * 
	 * @since 3.1
	 */
	public static final String PREF_TABLE_RENDERING_PAGE_SIZE = "org.eclispe.debug.ui.memory.pageSize"; //$NON-NLS-1$
	
	/**
	 * If the user should be promted of not in the event dupliacte launch delegates are detected during launching
	 * 
	 * @since 3.3
	 * EXPERIMENTAL
	 */
	public static final String PREF_PROMPT_FOR_DUPLICATE_DELEGATES = IDebugUIConstants.PLUGIN_ID + ".prompt_for_duplicate_delegates"; //$NON-NLS-1$
	
	/**
	 * The default action to perform if the user has specified not to be prompted any more
	 * The choices for this option are 'list', 'dialog', 'page'
	 * 
	 * @since 3.3
	 * EXPERIMENTAL
	 */
	public static final String PREF_DEFAULT_DUPLICATE_DELEGATE_ACTION = IDebugUIConstants.PLUGIN_ID + ".default_duplicate_delegate_action"; //$NON-NLS-1$
	
	/**
	 * Default page size when dynamic loading mode is off.  This preference is stored
	 * in number of lines.
	 * 
	 * @since 3.1
	 */
	public static final int DEFAULT_PAGE_SIZE = 20;
	/**
	 * Preference for defining behavior when resetting a memory monitor.
	 * Possible values:
	 * - RESET_AL - reset all renderings regardless if they are visible or not
	 * - RESET_VISIBLE - reset visible renderings
	 * 
	 * @since 3.2
	 */
	public static final String PREF_RESET_MEMORY_BLOCK = IDebugUIConstants.PLUGIN_ID + ".reset_memory_block"; //$NON-NLS-1$
	
	/**
	 * Constant to indicate that the memory view will reset all memory renderings when the reset
	 * memory monitor action is invoked.
	 * 
	 * @since 3.2
	 */
	public static final String RESET_ALL = IDebugUIConstants.PLUGIN_ID + "resetMemoryBlock.all"; //$NON-NLS-1$
	
	
	/**
	 * Constant to indicate that the memory view will reset visible memory renderings when
	 * the reset memory monitor action is invoked
	 * 
	 * @since 3.2
	 */
	public static final String RESET_VISIBLE = IDebugUIConstants.PLUGIN_ID + "resetMemoryBlock.visible"; //$NON-NLS-1$
	
	/**
	 * Preference identifier for the row size in a table rendering.
	 * This preference is expected to be saved by an </code>IPersistableDebugElement</code>.  
	 * Memory Blocks can optionally provide and save this preference to customize
	 * the initial format of a table rendering.
	 * 
	 * The value of this property is an Integer.  The value can be one of the
	 * following values:  1, 2, 4, 8, 16.  This value must be greater than 
	 * <code>PREF_COL_SIZE_BY_MODEL</code> and must also be divisible by <code>PREF_COL_SIZE_BY_MODEL</code>.
	 * 
	 * @since 3.2
	 */
	public static final String PREF_ROW_SIZE_BY_MODEL = "org.eclipse.debug.ui.AbstractTableRendering.rowSize"; //$NON-NLS-1$
	/**
	 * Preference identifier for the column size in a table rendering.
	 * This preference is expected to be saved by an <code>IPersistableDebugElement</code>.  
	 * Memory Blocks can optionally provide and save this preference to customize
	 * the initial format of a table rendering.
	 * 
	 * The value of this property is an Integer.  The value can be one of the
	 * following values:  1, 2, 4, 8, 16.  This value must be smaller than
	 * <code>PREF_ROW_SIZE_BY_MODEL</code>.  <code>PREF_ROW_SIZE_BY_MODEL</code> must be divisible by <code>PREF_COL_SIZE_BY_MODEL</code>.
	 * 
	 * @since 3.2
	 */
	public static final String PREF_COL_SIZE_BY_MODEL = "org.eclipse.debug.ui.AbstractTableRendering.colSize"; //$NON-NLS-1$
}


