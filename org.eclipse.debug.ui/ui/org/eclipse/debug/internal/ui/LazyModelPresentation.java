/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui;


import java.math.BigInteger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IDebugElement;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IMemoryBlock;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.internal.core.IDebugRuleFactory;
import org.eclipse.debug.internal.core.ListenerList;
import org.eclipse.debug.internal.ui.views.memory.IMemoryBlockModelPresentation;
import org.eclipse.debug.internal.ui.views.memory.IMemoryRenderingType;
import org.eclipse.debug.ui.IDebugEditorPresentation;
import org.eclipse.debug.ui.IDebugModelPresentation;
import org.eclipse.debug.ui.IValueDetailListener;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.IFontProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;

/**
 * A proxy to an IDebugModelPresentation extension. Instantiates the extension
 * when it is needed.
 */

public class LazyModelPresentation implements IDebugModelPresentation, IDebugEditorPresentation, IColorProvider, IFontProvider, IMemoryBlockModelPresentation {
	
	/**
	 * A temporary mapping of attribute ids to their values
	 * @see IDebugModelPresentation#setAttribute
	 */
	protected HashMap fAttributes= new HashMap(3);

	/**
	 * The config element that defines the extension
	 */
	protected IConfigurationElement fConfig = null;
	
	/**
	 * The actual presentation instance - null until called upon
	 */
	protected IDebugModelPresentation fPresentation = null;
	
	/**
	 * Temp holding for listeners - we do not add to presentation until
	 * it needs to be instantiated.
	 */
	protected ListenerList fListeners= new ListenerList(5);	
		
	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.IDebugEditorPresentation#removeAnntations(org.eclipse.ui.IEditorPart, org.eclipse.debug.core.model.IThread)
	 */
	public void removeAnnotations(IEditorPart editorPart, IThread thread) {
		IDebugModelPresentation presentation = getPresentation();
		if (presentation instanceof IDebugEditorPresentation) {
			((IDebugEditorPresentation)presentation).removeAnnotations(editorPart, thread);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.IDebugEditorPresentation#addAnnotations(org.eclipse.ui.IEditorPart, org.eclipse.debug.core.model.IStackFrame)
	 */
	public boolean addAnnotations(IEditorPart editorPart, IStackFrame frame) {
		IDebugModelPresentation presentation = getPresentation();
		if (presentation instanceof IDebugEditorPresentation) {
			return ((IDebugEditorPresentation)presentation).addAnnotations(editorPart, frame);
		}
		return false;
	}

	/**
	 * Constructs a lazy presentation from the config element.
	 */
	public LazyModelPresentation(IConfigurationElement configElement) {
		fConfig = configElement;
	}

	/**
	 * @see IDebugModelPresentation#getImage(Object)
	 */
	public Image getImage(Object element) {
	    Image image = null;
	    // TODO: causes UI job blocking dialog to appear
//	    ISchedulingRule rule = null;
//	    try {
//		    rule = beginRule(element);
		    image = getPresentation().getImage(element);
//	    } finally {
//	        endRule(rule);
//	    }
	    return image;
	}

	/**
	 * @see IDebugModelPresentation#getText(Object)
	 */
	public String getText(Object element) {
	    String text = null;
	    ISchedulingRule rule = null;
	    try {
		    rule = beginRule(element); 
			text = getPresentation().getText(element);
	    } finally {
	        endRule(rule);
	    }
		return text;
	}
	
	/**
     * @param rule
     */
    private void endRule(ISchedulingRule rule) {
        if (rule != null) {
            Platform.getJobManager().endRule(rule);
        }
    }

    /**
     * @param element
     * @return
     */
    private ISchedulingRule beginRule(Object object) {
        ISchedulingRule rule = null;
        if (object instanceof IDebugElement) {
            rule = DebugPlugin.accessRule((IDebugElement)object);
            if (rule != null) {
                Platform.getJobManager().beginRule(rule, null);
            }
        }
        return rule;
    }

    /**
	 * @see IDebugModelPresentation#computeDetail(IValue, IValueDetailListener)
	 */
	public void computeDetail(IValue value, IValueDetailListener listener) {
		getPresentation().computeDetail(value, listener);
	}	
	
	/**
	 * @see ISourcePresentation#getEditorInput(Object)
	 */
	public IEditorInput getEditorInput(Object element) {
		return getPresentation().getEditorInput(element);
	}
	
	/**
	 * @see ISourcePresentation#getEditorId(IEditorInput, Object)
	 */
	public String getEditorId(IEditorInput input, Object inputObject) {
		return getPresentation().getEditorId(input, inputObject);
	}

	/**
	 * @see IBaseLabelProvider#addListener(ILabelProviderListener)
	 */
	public void addListener(ILabelProviderListener listener) {
		if (fPresentation != null) {
			getPresentation().addListener(listener);
		}
		fListeners.add(listener);
	}

	/**
	 * @see IBaseLabelProvider#dispose()
	 */
	public void dispose() {
		if (fPresentation != null) {
			getPresentation().dispose();
		}
		fListeners = null;
	}

	/**
	 * @see IBaseLabelProvider#isLabelProperty(Object, String)
	 */
	public boolean isLabelProperty(Object element, String property) {
		if (fPresentation != null) {
			return getPresentation().isLabelProperty(element, property);
		} 
		return false;
	}

	/**
	 * @see IBaseLabelProvider#removeListener(ILabelProviderListener)
	 */
	public void removeListener(ILabelProviderListener listener) {
		if (fPresentation != null) {
			getPresentation().removeListener(listener);
		}
		ListenerList listeners = fListeners;
		if (listeners != null) {
		    listeners.remove(listener);
		}
	}
	
	/**
	 * Returns the real presentation, instantiating if required.
	 */
	protected IDebugModelPresentation getPresentation() {
		if (fPresentation == null) {
		    synchronized (this) {
		        if (fPresentation != null) {
		            // In the case that the synchronization is enforced, the "blocked" thread
		            // should return the presentation configured by the "owning" thread.
		            return fPresentation;
		        }
				try {
					IDebugModelPresentation tempPresentation= (IDebugModelPresentation) DebugUIPlugin.createExtension(fConfig, "class"); //$NON-NLS-1$
					// configure it
					if (fListeners != null) {
						Object[] list = fListeners.getListeners();
						for (int i= 0; i < list.length; i++) {
						    tempPresentation.addListener((ILabelProviderListener)list[i]);
						}
					}
					Iterator keys= fAttributes.keySet().iterator();
					while (keys.hasNext()) {
						String key= (String)keys.next();
						tempPresentation.setAttribute(key, fAttributes.get(key));
					}
					// Only assign to the instance variable after it's been configured. Otherwise,
					// the synchronization is defeated (a thread could return the presentation before
					// it's been configured).
					fPresentation= tempPresentation;
				} catch (CoreException e) {
					DebugUIPlugin.log(e);
				}
		    }
		}
		return fPresentation;
	}

	/**
	 * @see IDebugModelPresentation#setAttribute(String, Object)
	 */
	public void setAttribute(String id, Object value) {
		if (value == null) {
			return;
		}
		if (fPresentation != null) {
			getPresentation().setAttribute(id, value);
		}

		fAttributes.put(id, value);
	}
	
	/**
	 * Returns the identifier of the debug model this
	 * presentation is registered for.
	 */
	public String getDebugModelIdentifier() {
		return fConfig.getAttribute("id"); //$NON-NLS-1$
	}
	
	/**
	 * Returns a new source viewer configuration for the details
	 * area of the variables view, or <code>null</code> if
	 * unspecified.
	 * 
	 * @return source viewer configuration or <code>null</code>
	 * @exception CoreException if unable to create the specified
	 * 	source viewer configuration
	 */
	public SourceViewerConfiguration newDetailsViewerConfiguration() throws CoreException {
		String attr  = fConfig.getAttribute("detailsViewerConfiguration"); //$NON-NLS-1$
		if (attr != null) {
			return (SourceViewerConfiguration)fConfig.createExecutableExtension("detailsViewerConfiguration"); //$NON-NLS-1$
		}
		return null;
	}
	
	/**
	 * Returns a copy of the attributes in this model presentation.
	 * 
	 * @return a copy of the attributes in this model presentation
	 * @since 3.0
	 */
	public Map getAttributeMap() {
		return (Map) fAttributes.clone();
	}

    /* (non-Javadoc)
     * @see org.eclipse.jface.viewers.IColorProvider#getForeground(java.lang.Object)
     */
    public Color getForeground(Object element) {
        IDebugModelPresentation presentation = getPresentation();
        if (presentation instanceof IColorProvider) {
            IColorProvider colorProvider = (IColorProvider) presentation;
            return colorProvider.getForeground(element);
        }
        return null;
    }

    /* (non-Javadoc)
     * @see org.eclipse.jface.viewers.IColorProvider#getBackground(java.lang.Object)
     */
    public Color getBackground(Object element) {
        IDebugModelPresentation presentation = getPresentation();
        if (presentation instanceof IColorProvider) {
            IColorProvider colorProvider = (IColorProvider) presentation;
            return colorProvider.getBackground(element);
        }
        return null;
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.jface.viewers.IFontProvider#getFont(java.lang.Object)
     */
    public Font getFont(Object element) {
        IDebugModelPresentation presentation = getPresentation();
        if (presentation instanceof IFontProvider) {
            IFontProvider fontProvider = (IFontProvider) presentation;
            return fontProvider.getFont(element);
        }
        return null;
    }

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.views.memory.IMemoryBlockModelPresentation#getTabLabel(org.eclipse.debug.core.model.IMemoryBlock, java.lang.String)
	 */
	public String getTabLabel(IMemoryBlock blk, String renderingId) {

		IDebugModelPresentation presentation = getPresentation();
		if (presentation instanceof IMemoryBlockModelPresentation)
		{
			IMemoryBlockModelPresentation mbp = (IMemoryBlockModelPresentation)presentation;
			return mbp.getTabLabel(blk, renderingId);
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.views.memory.IMemoryBlockModelPresentation#getColumnLabels(org.eclipse.debug.core.model.IMemoryBlock, int, int)
	 */
	public String[] getColumnLabels(IMemoryBlock blk, int bytesPerLine, int columnSize) {
		IDebugModelPresentation presentation = getPresentation();
		if (presentation instanceof IMemoryBlockModelPresentation)
		{
			IMemoryBlockModelPresentation mbp = (IMemoryBlockModelPresentation)presentation;
			return mbp.getColumnLabels(blk, bytesPerLine, columnSize);
		}
		return new String[0];
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.views.memory.IMemoryBlockModelPresentation#getAddressPresentation(org.eclipse.debug.core.model.IMemoryBlock, java.math.BigInteger)
	 */
	public String getAddressPresentation(IMemoryBlock blk, BigInteger address) {
		IDebugModelPresentation presentation = getPresentation();
		if (presentation instanceof IMemoryBlockModelPresentation)
		{
			IMemoryBlockModelPresentation mbp = (IMemoryBlockModelPresentation)presentation;
			return mbp.getAddressPresentation(blk, address);
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.views.memory.IMemoryBlockModelPresentation#getViewPaneIdForDefault(org.eclipse.debug.core.model.IMemoryBlock, org.eclipse.debug.internal.ui.views.memory.IMemoryRenderingType)
	 */
	public String getViewPaneIdForDefault(IMemoryBlock blk, IMemoryRenderingType renderingType) {
		IDebugModelPresentation presentation = getPresentation();
		if (presentation instanceof IMemoryBlockModelPresentation)
		{
			IMemoryBlockModelPresentation mbp = (IMemoryBlockModelPresentation)presentation;
			return mbp.getViewPaneIdForDefault(blk, renderingType);
		}
		return null;
	}    
}
