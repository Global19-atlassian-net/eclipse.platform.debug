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

import org.eclipse.ui.IMemento;

/**
 * Request to compare an element to a previously created memento.
 * 
 * @since 3.3
 */
public interface IElementCompareRequest extends IPresentationUpdate {

	/**
	 * The element to compare against this request's memento.
	 * 
	 * @return element
	 */
	public Object getElement();
	
	/**
	 * The memento to compare this request's element.
	 * 
	 * @return memento
	 */
	public IMemento getMemento();
	
	/**
	 * Sets whether this request's memento represents this requests's element.
	 * 
	 * @param equal whether the memento represents the element 
	 */
	public void setEqual(boolean equal);
}
