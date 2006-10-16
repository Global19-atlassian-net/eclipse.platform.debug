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
package org.eclipse.debug.internal.ui.contexts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.contexts.provisional.DebugContextEvent;
import org.eclipse.debug.internal.ui.contexts.provisional.IDebugContextEventListener;
import org.eclipse.debug.internal.ui.contexts.provisional.IDebugContextListener;
import org.eclipse.debug.internal.ui.contexts.provisional.IDebugContextProvider;
import org.eclipse.debug.internal.ui.contexts.provisional.IDebugContextService;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;

/**
 * Context service for a specific window.
 * 
 * @since 3.2
 */
public class DebugWindowContextService implements IDebugContextService, IPartListener2, IDebugContextEventListener {
	
	private Map fListenersByPartId = new HashMap();
	private Map fProvidersByPartId = new HashMap();
	private Map fPostListenersByPartId = new HashMap();
	
	private IWorkbenchWindow fWindow;
	private List fProviders = new ArrayList();

	public DebugWindowContextService(IWorkbenchWindow window) {
		fWindow = window;
		fWindow.getPartService().addPartListener(this);
	}
	
	public void dispose() {
		fWindow.getPartService().removePartListener(this);
		fWindow = null;
	}
	
	protected synchronized void addProvider(IDebugContextProvider provider) {
		IWorkbenchPart part = provider.getPart();
		String id = null;
		if (part != null) {
			id = part.getSite().getId();
		}
		fProvidersByPartId.put(id, provider);
		fProviders.add(provider);
		IWorkbenchPart active = null;
		IWorkbenchPage activePage = fWindow.getActivePage();
		if (activePage != null) {
			active = activePage.getActivePart();
		}
		if (fProviders.size() == 1 && (part == null || part.equals(active))) {
			notify(DebugContextEvent.ACTIVATED);
		}
		provider.addDebugContextEventListener(this);
	}
	
	protected synchronized void removeProvider(IDebugContextProvider provider) {
		int index = fProviders.indexOf(provider);
		if (index >= 0) {
			IWorkbenchPart part = provider.getPart();
			String id = null;
			if (part != null) {
				id = part.getSite().getId();
			}
			fProvidersByPartId.remove(id);
			fProviders.remove(index);
			if (index == 0) {
				notify(DebugContextEvent.ACTIVATED);
			}
		}
		provider.removeDebugContextEventListener(this);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.contexts.IDebugContextService#addDebugContextListener(org.eclipse.debug.ui.contexts.IDebugContextListener)
	 */
	public void addDebugContextListener(IDebugContextListener listener) {
		addDebugContextListener(listener, null);
	}
	
	public void addPostDebugContextListener(IDebugContextListener listener, String partId) {
		ListenerList list = (ListenerList) fPostListenersByPartId.get(partId);
		if (list == null) {
			list = new ListenerList();
			fPostListenersByPartId.put(partId, list);
		}
		list.add(listener);	
	}

	public void addPostDebugContextListener(IDebugContextListener listener) {
		addPostDebugContextListener(listener, null);
	}
	
	public void removePostDebugContextListener(IDebugContextListener listener, String partId) {
		ListenerList list = (ListenerList) fPostListenersByPartId.get(partId);
		if (list != null) {
			list.remove(listener);
		}
	}

	public void removePostDebugContextListener(IDebugContextListener listener) {
		removePostDebugContextListener(listener, null);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.contexts.IDebugContextService#removeDebugContextListener(org.eclipse.debug.ui.contexts.IDebugContextListener)
	 */
	public void removeDebugContextListener(IDebugContextListener listener) {
		removeDebugContextListener(listener, null);
	}
	
	protected void notify(int type) {
		if (fProviders.isEmpty()) {
			notify(type, new StructuredSelection(), null);
		} else {
			IDebugContextProvider provider = (IDebugContextProvider) fProviders.get(0);
			notify(type, provider.getActiveContext(), provider.getPart());
		}
	}
	
	protected void notify(int type, ISelection context, IWorkbenchPart part) {
		notify(type, getListeners(null), context, part);
		if (part != null) {
			notify(type, getListeners(part), context, part);
		}
		notify(type, getPostListeners(null), context, part);
		if (part != null) {
			notify(type, getPostListeners(part), context, part);
		}
	}
	
	protected void notify(final int type, ListenerList list, final ISelection context, final IWorkbenchPart part) {
		if (list != null) {
			Object[] listeners = list.getListeners();
			for (int i = 0; i < listeners.length; i++) {
				final IDebugContextListener listener = (IDebugContextListener) listeners[i];
				SafeRunner.run(new ISafeRunnable() {
					public void run() throws Exception {
						if (type == DebugContextEvent.ACTIVATED) {
							listener.contextActivated(context, part);
						} else {
							listener.contextChanged(context, part);
						}
					}
					public void handleException(Throwable exception) {
						DebugUIPlugin.log(exception);
					}
				});
			}
		}
	}
	
	protected ListenerList getListeners(IWorkbenchPart part) {
		String id = null;
		if (part != null) {
			id = part.getSite().getId();
		}
		return (ListenerList) fListenersByPartId.get(id);
	}
	
	protected ListenerList getPostListeners(IWorkbenchPart part) {
		String id = null;
		if (part != null) {
			id = part.getSite().getId();
		}
		return (ListenerList) fPostListenersByPartId.get(id);
	}	

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.contexts.IDebugContextService#addDebugContextListener(org.eclipse.debug.ui.contexts.IDebugContextListener, java.lang.String)
	 */
	public synchronized void addDebugContextListener(IDebugContextListener listener, String partId) {
		ListenerList list = (ListenerList) fListenersByPartId.get(partId);
		if (list == null) {
			list = new ListenerList();
			fListenersByPartId.put(partId, list);
		}
		list.add(listener);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.contexts.IDebugContextService#removeDebugContextListener(org.eclipse.debug.ui.contexts.IDebugContextListener, java.lang.String)
	 */
	public void removeDebugContextListener(IDebugContextListener listener, String partId) {
		ListenerList list = (ListenerList) fListenersByPartId.get(partId);
		if (list != null) {
			list.remove(listener);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.contexts.IDebugContextService#getActiveContext(java.lang.String)
	 */
	public ISelection getActiveContext(String partId) {
		IDebugContextProvider provider = (IDebugContextProvider) fProvidersByPartId.get(partId);
		if (provider != null) {
			return provider.getActiveContext();
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.contexts.IDebugContextService#getActiveContext()
	 */
	public ISelection getActiveContext() {
		if (!fProviders.isEmpty()) {
			return ((IDebugContextProvider)fProviders.get(0)).getActiveContext();
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPartListener2#partActivated(org.eclipse.ui.IWorkbenchPartReference)
	 */
	public void partActivated(IWorkbenchPartReference partRef) {
		Object provider = fProvidersByPartId.get(partRef.getId());
		if (provider != null) {
			int index = fProviders.indexOf(provider);
			if (index > 0) {
				fProviders.remove(index);
				fProviders.add(0, provider);
				notify(DebugContextEvent.ACTIVATED);
			}
		}
		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPartListener2#partBroughtToTop(org.eclipse.ui.IWorkbenchPartReference)
	 */
	public void partBroughtToTop(IWorkbenchPartReference partRef) {		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPartListener2#partClosed(org.eclipse.ui.IWorkbenchPartReference)
	 */
	public synchronized void partClosed(IWorkbenchPartReference partRef) {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPartListener2#partDeactivated(org.eclipse.ui.IWorkbenchPartReference)
	 */
	public void partDeactivated(IWorkbenchPartReference partRef) {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPartListener2#partOpened(org.eclipse.ui.IWorkbenchPartReference)
	 */
	public void partOpened(IWorkbenchPartReference partRef) {		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPartListener2#partHidden(org.eclipse.ui.IWorkbenchPartReference)
	 */
	public void partHidden(IWorkbenchPartReference partRef) {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPartListener2#partVisible(org.eclipse.ui.IWorkbenchPartReference)
	 */
	public void partVisible(IWorkbenchPartReference partRef) {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPartListener2#partInputChanged(org.eclipse.ui.IWorkbenchPartReference)
	 */
	public void partInputChanged(IWorkbenchPartReference partRef) {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.contexts.provisional.IDebugContextEventListener#contextEvent(org.eclipse.debug.internal.ui.contexts.provisional.DebugContextEvent)
	 */
	public void contextEvent(DebugContextEvent event) {
		if (!fProviders.isEmpty()) {
			IDebugContextProvider provider = (IDebugContextProvider) fProviders.get(0);
			if (provider == event.getDebugContextProvider()) {
				notify(event.getEventType());
			}
		}	
	}

}
