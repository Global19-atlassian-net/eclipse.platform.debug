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

package org.eclipse.debug.internal.ui.viewers.update;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IExpressionManager;
import org.eclipse.debug.core.IExpressionsListener;
import org.eclipse.debug.core.model.IExpression;
import org.eclipse.debug.internal.ui.viewers.provisional.AbstractModelProxy;
import org.eclipse.debug.internal.ui.viewers.provisional.IModelDelta;
import org.eclipse.debug.internal.ui.viewers.provisional.IPresentationContext;
import org.eclipse.debug.internal.ui.viewers.provisional.ModelDelta;


public class ExpressionManagerModelProxy extends AbstractModelProxy implements IExpressionsListener {
        
	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.viewers.update.EventHandlerModelProxy#init(org.eclipse.debug.internal.ui.viewers.IPresentationContext)
	 */
	public void init(IPresentationContext context) {
		super.init(context);
		getExpressionManager().addExpressionListener(this);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.viewers.provisional.AbstractModelProxy#installed()
	 */
	public void installed() {
		updateExpressions(getExpressionManager().getExpressions(), IModelDelta.INSTALL);
	}

	/**
	 * @return
	 */
	protected IExpressionManager getExpressionManager() {
		return DebugPlugin.getDefault().getExpressionManager();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.viewers.update.EventHandlerModelProxy#dispose()
	 */
	public synchronized void dispose() {
		super.dispose();
		getExpressionManager().removeExpressionListener(this);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.IExpressionsListener#expressionsAdded(org.eclipse.debug.core.model.IExpression[])
	 */
	public void expressionsAdded(IExpression[] expressions) {
		updateExpressions(expressions, IModelDelta.ADDED | IModelDelta.INSTALL);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.IExpressionsListener#expressionsRemoved(org.eclipse.debug.core.model.IExpression[])
	 */
	public void expressionsRemoved(IExpression[] expressions) {
		updateExpressions(expressions, IModelDelta.REMOVED | IModelDelta.UNINSTALL);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.IExpressionsListener#expressionsChanged(org.eclipse.debug.core.model.IExpression[])
	 */
	public void expressionsChanged(IExpression[] expressions) {
		updateExpressions(expressions, IModelDelta.CONTENT | IModelDelta.STATE);		
	}
    
    private void updateExpressions(IExpression[] expressions, int flags) {
		ModelDelta delta = new ModelDelta(getExpressionManager(), IModelDelta.NO_CHANGE);
		for (int i = 0; i < expressions.length; i++) {
			IExpression expression = expressions[i];
			delta.addNode(expression, flags);
		}
		fireModelChanged(delta);
    }

}
