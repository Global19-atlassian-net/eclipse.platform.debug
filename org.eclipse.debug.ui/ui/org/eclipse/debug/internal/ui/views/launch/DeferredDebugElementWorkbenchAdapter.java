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
package org.eclipse.debug.internal.ui.views.launch;

import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.debug.core.model.IDebugElement;
import org.eclipse.debug.internal.core.IDebugRuleFactory;
import org.eclipse.debug.ui.DebugElementWorkbenchAdapter;
import org.eclipse.ui.progress.IDeferredWorkbenchAdapter;


/**
 * Default deferred content provider for a debug target 
 */
public abstract class DeferredDebugElementWorkbenchAdapter extends DebugElementWorkbenchAdapter implements IDeferredWorkbenchAdapter {
    
    /* (non-Javadoc)
     * @see org.eclipse.ui.progress.IDeferredWorkbenchAdapter#isContainer()
     */
    public boolean isContainer() {
        return true;
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.progress.IDeferredWorkbenchAdapter#getRule(java.lang.Object)
     */
    public ISchedulingRule getRule(Object object) {
        if (object instanceof IDebugElement) {
            IDebugElement element = (IDebugElement)object;
            IDebugRuleFactory factory = (IDebugRuleFactory) element.getAdapter(IDebugRuleFactory.class);
            if (factory != null) {
                return factory.accessRule(element);
            }
        }
        return null;
    }


}
