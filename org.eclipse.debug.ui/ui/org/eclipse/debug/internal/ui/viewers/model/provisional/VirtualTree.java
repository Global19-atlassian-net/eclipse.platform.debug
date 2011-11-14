/*******************************************************************************
 * Copyright (c) 2011 Wind River Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.viewers.model.provisional;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.swt.SWT;


/**
 * Tree of virtual items that is analogous to SWT's tree control.  The tree is used
 * by the {@link VirtualTreeModelViewer}. 
 * 
 * @since 3.8
 * 
 * @see VirtualTreeModelViewer
 */
public class VirtualTree extends VirtualItem {

    /**
     * Lazy virtual tree does not retrieve elements or labels,
     * except for the selected elements.
     */
    private boolean fLazy;
    
    private IVirtualItemValidator fValidator;
    
    private class SelectedItemValidator implements IVirtualItemValidator {
        public boolean isItemVisible(VirtualItem item) {
            // visible items.  For now only mark the selected items as visible.
            for (int i = 0; i < fSelection.length; i++) {
                VirtualItem selectionItem = fSelection[i]; 
                while (selectionItem != null) {
                    if (item.equals(selectionItem)) {
                        return true;
                    }
                    selectionItem = selectionItem.getParent();
                }
            }
            return false;
        }
        
        public void showItem(VirtualItem item) {
        }
    }
    
    /**
     * Interface for listeners that need to be notified when items 
     * are disposed or revealed.  It  should be implemented by the viewer.
     */
    public static interface IVirtualItemListener {
        
        /**
         * Called when the item has been shown in the virtual viewer's 
         * view-port.  This indicates to the viewer that it should check
         * the item's status and request needed data.
         * 
         * @param item The item that was revealed.
         */
        public void revealed(VirtualItem item);
        
        /**
         * Called when an item is disposed.  It tells the viewer to
         * clean up any remaining mappings and cached data of this item.
         * 
         * @param item The itam that was disposed.
         */
        public void disposed(VirtualItem item);
    }
    
    /**
     * Set of listeners of the virtual tree.
     */
    private Set fVirtualItemListeners = new HashSet(1);

    /**
     * The currently selected items.  This array contains only
     * the leaf items which are selected.
     */
    private VirtualItem[] fSelection = new VirtualItem[0];
    
    /**
     * Constructs the virtual tree with the given style and validator.
     * 
     * @param style The style flag.  Only SWT.VIRTUAL flag is used.
     * @param validator Item validator used to determine item visibility.
     */
    public VirtualTree(int style, IVirtualItemValidator validator) {
        super(null, new VirtualItem.Index(0));
        fLazy = (style & SWT.VIRTUAL) != 0;
        if (fLazy && validator == null) {
            fValidator = new SelectedItemValidator();
        } else { 
            fValidator = validator;
        }
        clearNeedsLabelUpdate();
        clearNeedsDataUpdate();
    }
    
    /**
     * Disposes the virtual tree.
     */
    public void dispose() {
        super.dispose();
        fVirtualItemListeners.clear();
    }

    public void setNeedsCountUpdate() {
        super.setNeedsCountUpdate();
        clearNeedsLabelUpdate();
        clearNeedsDataUpdate();
    }
    
    public void setNeedsLabelUpdate() {
        // no-op
    }

    public void setData(String key, Object data) {
        super.setData(key, data);
        if (data == null) {
            clearNeedsDataUpdate();
        }
    }
    
    /**
     * Adds a listener for when virtual items are revealed in the view.   
     * @param listener Listener to add to list of listeners.
     */
    public void addItemListener(IVirtualItemListener listener) {
        fVirtualItemListeners.add(listener);
    }

    public void removeItemListener(IVirtualItemListener listener) {
        fVirtualItemListeners.remove(listener);
    }

    public VirtualItem[] getSelection() {
        return fSelection;
    }
    
    public void setSelection(VirtualItem[] items) {
        fSelection = items;
    }
    
    public void showItem(VirtualItem item) {
        if (fValidator != null) {
            fValidator.showItem(item);
        }
    }
    
    public void fireItemDisposed(VirtualItem item) {
        for (Iterator itr = fVirtualItemListeners.iterator(); itr.hasNext();) {
            ((IVirtualItemListener)itr.next()).disposed(item);
        }
    }
    
    public void fireItemRevealed(VirtualItem item) {
        for (Iterator itr = fVirtualItemListeners.iterator(); itr.hasNext();) {
            ((IVirtualItemListener)itr.next()).revealed(item);
        }        
    }

    public void setData(Object data) {
        super.setData(data);
        // The root item always has children as long as the input is non-null, 
        // so that it should be expanded.
        setHasItems(data != null);
    }

    public void setHasItems(boolean hasChildren) {
        super.setHasItems(hasChildren);
        // The root item is always expanded as long as it has children. 
        if (hasChildren) {
            setExpanded(true);
        }
    }
    
    /**
     * Returns whether the given item is considered visible by the tree as 
     * determined by its virtual item validator.
     *  
     * @param item Item to check.
     * @return true if items is vislble.
     * @see IVirtualItemValidator
     */
    public boolean isItemVisible(VirtualItem item) {
        if (fLazy) {
            return fValidator.isItemVisible(item);
        }
        return true;
    }

    /**
     * Validates the entire tree.
     */
    public void validate() {
        validate(VirtualTree.this);
    }
    
    /**
     * Validates the item and its children, identifying children which were 
     * revealed and need to be updated.
     * 
     * @param item The item which to validate.
     */
    public void validate(VirtualItem item) {
        if (item.needsDataUpdate()) {
            if (isItemVisible(item)) {
                fireItemRevealed(item);
            }
        } else if (item.getData() != null) {
            if ( item.needsLabelUpdate() || (item.needsCountUpdate() && item.hasItems() && item.getExpanded()) ) {
                if (isItemVisible(item)) {
                    fireItemRevealed(item);
                }
            }
            
            if (item.getData() != null && item.getItemCount() > 0 && item.getExpanded()) {
                for (int i = 0; i < item.getItemCount(); i++) {
                    validate(item.getItem(new Index(i)));
                }
            }
        }
    }
}
