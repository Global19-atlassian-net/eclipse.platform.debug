/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.model;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;

/**
 * Context sensitive label update request for an element.
 *  
 * @since 3.3
 */
public interface ILabelUpdate extends IPresentationUpdate {
	
	/**
	 * Returns the element the label update is for.
	 * 
	 * @return associated element
	 */
	public Object getElement();
	
	/**
	 * Returns the column the label is for, or <code>null</code> if no columns.
	 * 
	 * @return column id or <code>null</code>
	 */
	public String getColumnId();

	/**
	 * Sets the text of the label. Cannot be <code>null</code>.
	 * 
	 * @param text
	 */
    public void setLabel(String text);
    
    /**
     * Sets the font of the label.
     * 
     * @param fontData
     */
    public void setFontData(FontData fontData);
    
    /**
     * Sets the image of the label.
     * 
     * @param image
     */
    public void setImageDescriptor(ImageDescriptor image);
    
    /**
     * Sets the foreground color of the label.
     * 
     * @param foreground
     */
    public void setForeground(RGB foreground);
    
    /**
     * Sets the background color of the label.
     * 
     * @param background
     */
    public void setBackground(RGB background);
}
