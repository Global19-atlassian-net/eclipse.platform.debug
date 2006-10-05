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

import org.eclipse.debug.core.IExpressionManager;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IExpression;
import org.eclipse.debug.core.model.IMemoryBlock;
import org.eclipse.debug.core.model.IMemoryBlockRetrieval;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IWatchExpression;
import org.eclipse.debug.internal.ui.memory.provisional.MemoryViewPresentationContext;
import org.eclipse.debug.internal.ui.viewers.provisional.IModelProxy;
import org.eclipse.debug.internal.ui.viewers.provisional.IModelProxyFactoryAdapter;
import org.eclipse.debug.internal.ui.viewers.provisional.IPresentationContext;
import org.eclipse.debug.ui.IDebugUIConstants;

public class DefaultModelProxyFactory implements IModelProxyFactoryAdapter {

	public IModelProxy createModelProxy(Object element, IPresentationContext context) {
		String id = context.getId();
		if (IDebugUIConstants.ID_DEBUG_VIEW.equals(id)) {
			if (element instanceof IDebugTarget) {
				return new DebugTargetProxy((IDebugTarget)element);
			}
			if (element instanceof ILaunchManager) {
				return new LaunchManagerProxy();
			}
			if (element instanceof IProcess) {
				return new ProcessProxy((IProcess)element);
			}
		}
		if (IDebugUIConstants.ID_VARIABLE_VIEW.equals(id)) {
			if (element instanceof IStackFrame) {
				return new DefaultVariableViewModelProxy((IStackFrame)element);
			}
		}
		if (IDebugUIConstants.ID_EXPRESSION_VIEW.equals(id)) {
			if (element instanceof IExpressionManager) {
				return new ExpressionManagerModelProxy();
			} 
			if (element instanceof IWatchExpression) {
				return new DefaultWatchExpressionModelProxy((IWatchExpression)element);
			}
			if (element instanceof IExpression) {
				return new DefaultExpressionModelProxy((IExpression)element);
			}
		}
		if (IDebugUIConstants.ID_REGISTER_VIEW.equals(id)) {
			if (element instanceof IStackFrame) {
				return new DefaultVariableViewModelProxy((IStackFrame)element);
			}
		}
		if (IDebugUIConstants.ID_MEMORY_VIEW.equals(id)) {
			if (element instanceof IMemoryBlockRetrieval)
				return new MemoryRetrievalProxy((IMemoryBlockRetrieval)element);
			if (context instanceof MemoryViewPresentationContext)
			{
				if (((MemoryViewPresentationContext)context).getRendering() != null)
				{
					if (element instanceof IMemoryBlock)
						return new MemoryBlockProxy((IMemoryBlock)element);
				}
			}
		}
		return null;
	}

}
