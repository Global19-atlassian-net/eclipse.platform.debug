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
package org.eclipse.debug.internal.ui.launchConfigurations;

 
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.core.expressions.EvaluationResult;
import org.eclipse.core.expressions.Expression;
import org.eclipse.core.expressions.ExpressionConverter;
import org.eclipse.core.expressions.ExpressionTagNames;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.Pair;
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPluginContribution;
import org.osgi.framework.Bundle;


/**
 * Proxy to a launch shortcut extension
 */
public class LaunchShortcutExtension implements ILaunchShortcut, IPluginContribution {
	
	private ImageDescriptor fImageDescriptor = null;
	private List fPerspectives = null;
	private ILaunchShortcut fDelegate = null;
	private Set fModes = null;
	private IConfigurationElement fContextualLaunchConfigurationElement = null;
	private Expression fContextualLaunchExpr = null;
	private Expression fStandardLaunchExpr = null;
	
	/**
	 * The configuration element defining this tab.
	 */
	private IConfigurationElement fConfig;
	private /* <Pair> */ List fContextLabels;
	
	/**
	 * Constructs a launch configuration tab extension based
	 * on the given configuration element
	 * 
	 * @param element the configuration element defining the
	 *  attribtues of this launch configuration tab extension
	 * @return a new launch configuration tab extension
	 */
	public LaunchShortcutExtension(IConfigurationElement element) {
		setConfigurationElement(element);
	}
	
	/**
	 * Sets the configuration element that defines the attributes
	 * for this extension.
	 * 
	 * @param element configuration element
	 */
	private void setConfigurationElement(IConfigurationElement element) {
		fConfig = element;
	}
	
	/**
	 * Returns the configuration element that defines the attributes
	 * for this extension.
	 * 
	 * @param configuration element that defines the attributes
	 *  for this launch configuration tab extension
	 */
	public IConfigurationElement getConfigurationElement() {
		return fConfig;
	}
	
	/**
	 * Returns the label of this shortcut
	 * 
	 * @return the label of this shortcut, or <code>null</code> if not
	 *  specified
	 */
	public String getLabel() {
		return getConfigurationElement().getAttribute("label"); //$NON-NLS-1$
	}
	
	/**
	 * Returns the configuration element for the optional Contextual Launch
	 * element of this Launch Configuration description.
	 * @return contextualLaunch element
	 */
	public IConfigurationElement getContextualLaunchConfigurationElement() {
		if (fContextualLaunchConfigurationElement == null) {
			IConfigurationElement[] elements = getConfigurationElement().getChildren("contextualLaunch"); //$NON-NLS-1$
			if (elements.length > 0) {
				// remember so we don't have to hunt again
				fContextualLaunchConfigurationElement = elements[0];
			}
		}
		return fContextualLaunchConfigurationElement;
	}
	/**
	 * Returns the contextual launch label of this shortcut for the named mode.
	 * <p>
	 * <samp>
	 * <launchShortcut...>
	 *   <contextualLaunch>
	 *     <contextLabel mode="run" label="Run Java Application"/>
	 *     <contextLabel mode="debug" label="Debug Java Application"/>
	 *     ...
	 *   </contextualLaunch>
	 * </launchShortcut>
	 * </samp>
	 * 
	 * @return the contextual label of this shortcut, or <code>null</code> if not
	 *  specified
	 */
	public String getContextLabel(String mode) {
		// remember the list of context labels for this shortcut
		if (fContextLabels == null) {
			IConfigurationElement context = getContextualLaunchConfigurationElement();
			if (context == null) {
				return null;
			}
			IConfigurationElement[] labels = context.getChildren("contextLabel"); //$NON-NLS-1$
			fContextLabels = new ArrayList(labels.length);
			for (int i = 0; i < labels.length; i++) {
				fContextLabels.add(new Pair(labels[i].getAttribute("mode"), //$NON-NLS-1$
						labels[i].getAttribute("label"))); //$NON-NLS-1$
			}
		}
		// pick out the first occurance of the "name" bound to "mode"
		Iterator iter = fContextLabels.iterator();
		while (iter.hasNext()) {
			Pair p = (Pair) iter.next();
			if (p.firstAsString().equals(mode)) {
				return p.secondAsString();
			}
		}
		return getLabel();
	}
	
	/**
	 * Evaluate the given expression within the given context and return
	 * the result. Returns <code>true</code> iff result is either TRUE or NOT_LOADED.
	 * This allows optimistic inclusion of shortcuts before plugins are loaded.
	 * Returns <code>false</code> if exp is <code>null</code>.
	 * 
	 * @param exp the enablement expression to evaluate or <code>null</code>
	 * @param context the context of the evaluation. Usually, the
	 *  user's selection.
	 * @return the result of evaluating the expression
	 * @throws CoreException
	 */
	public boolean evalEnablementExpression(IEvaluationContext context, Expression exp) throws CoreException {
		return (exp != null) ? ((exp.evaluate(context)) != EvaluationResult.FALSE) : false;
	}
	
	/**
	 * Returns an expression that represents the enablement logic for the
	 * contextual launch element of this launch shortcut description or
	 * <code>null</code> if none.
	 * @return an evaluatable expression or <code>null</code>
	 * @throws CoreException if the configuration element can't be
	 *  converted. Reasons include: (a) no handler is available to
	 *  cope with a certain configuration element or (b) the XML
	 *  expression tree is malformed.
	 */
	public Expression getContextualLaunchEnablementExpression() throws CoreException {
		// all of this stuff is optional, so...tedius testing is required
		if (fContextualLaunchExpr == null) {
			IConfigurationElement contextualLaunchElement = getContextualLaunchConfigurationElement();
			if (contextualLaunchElement == null) {
				// not available
				return null;
			}
			IConfigurationElement[] elements = contextualLaunchElement.getChildren(ExpressionTagNames.ENABLEMENT);
			IConfigurationElement enablement = elements.length > 0 ? elements[0] : null; 

			if (enablement != null) {
				fContextualLaunchExpr= ExpressionConverter.getDefault().perform(enablement);
			}
		}
		return fContextualLaunchExpr;
	}
	
	/**
	 * Returns an expression that represents the enablement logic for the
	 * launch shortcut description or <code>null</code> if none.
	 * @return an evaluatable expression or <code>null</code>
	 * @throws CoreException if the configuration element can't be
	 *  converted. Reasons include: (a) no handler is available to
	 *  cope with a certain configuration element or (b) the XML
	 *  expression tree is malformed.
	 */
	public Expression getShortcutEnablementExpression() throws CoreException {
		// all of this stuff is optional, so...tedius testing is required
		if (fStandardLaunchExpr == null) {
			IConfigurationElement[] elements = getConfigurationElement().getChildren(ExpressionTagNames.ENABLEMENT);
			IConfigurationElement enablement = elements.length > 0 ? elements[0] : null; 
			if (enablement != null) {
				fStandardLaunchExpr= ExpressionConverter.getDefault().perform(enablement);
			}
		}
		return fStandardLaunchExpr;
	}	
	
	/**
	 * Returns the id of this shortcut
	 * 
	 * @return the id of this shortcut, or <code>null</code> if not specified
	 */
	public String getId() {
		return getConfigurationElement().getAttribute("id"); //$NON-NLS-1$
	}
	
	/**
	 * Returns the path of the icon for this shortcut, or <code>null</code>
	 * if none.
	 * 
	 * @return the path of the icon for this shortcut, or <code>null</code>
	 * if none
	 */
	protected String getIconPath() {
		return getConfigurationElement().getAttribute("icon"); //$NON-NLS-1$
	}	
	
	/**
	 * Returns the identifier of the help context associated with this launch
	 * shortcut, or <code>null</code> if one was not specified.
	 * 
	 * @return the identifier of this launch shortcut's help context or
	 * <code>null</code>
	 * @since 2.1
	 */	
	public String getHelpContextId() {
		return getConfigurationElement().getAttribute("helpContextId"); //$NON-NLS-1$		
	}
	
	/**
	 * Returns the category of this shortcut
	 *
	 * @return the category of this shortcut, or <code>null</code> if not
	 *  specified
	 */
	public String getCategory() {
		return getConfigurationElement().getAttribute("category"); //$NON-NLS-1$
	}	
	
	/**
	 * Returns the image for this shortcut, or <code>null</code> if none
	 * 
	 * @return the image for this shortcut, or <code>null</code> if none
	 */
	public ImageDescriptor getImageDescriptor() {
		if (fImageDescriptor == null) {
			String iconPath = getIconPath();
			// iconPath may be null because imageIcon is optional
			if (iconPath != null) {
				try {
					Bundle bundle = Platform.getBundle(getConfigurationElement().getDeclaringExtension().getNamespace());
					URL iconURL = bundle.getEntry("/"); //$NON-NLS-1$
					iconURL = new URL(iconURL, iconPath);
					fImageDescriptor = ImageDescriptor.createFromURL(iconURL);
				} catch (MalformedURLException e) {
					DebugUIPlugin.log(e);
				}
			}
		}
		return fImageDescriptor;
	}
	
	/**
	 * Returns the perspectives this shortcut is registered for.
	 * 
	 * @return list of Strings representing perspective identifiers 
	 */
	public List getPerspectives() {
		if (fPerspectives == null) {
			IConfigurationElement[] perspectives = getConfigurationElement().getChildren("perspective"); //$NON-NLS-1$
			fPerspectives = new ArrayList(perspectives.length);
			for (int i = 0; i < perspectives.length; i++) {
				fPerspectives.add(perspectives[i].getAttribute("id")); //$NON-NLS-1$
			}
		}
		return fPerspectives;
	}
	
	/**
	 * Returns this shortcut's delegate, or <code>null</code> if none
	 * 
	 * @return this shortcut's delegate, or <code>null</code> if none
	 */
	protected ILaunchShortcut getDelegate() {
		if (fDelegate == null) {
			try {
				fDelegate = (ILaunchShortcut)fConfig.createExecutableExtension("class"); //$NON-NLS-1$
			} catch (CoreException e) {
				DebugUIPlugin.errorDialog(DebugUIPlugin.getShell(), LaunchConfigurationsMessages.getString("LaunchShortcutExtension.Error_4"), LaunchConfigurationsMessages.getString("LaunchShortcutExtension.Unable_to_use_launch_shortcut_5"), e.getStatus()); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		return fDelegate;
	}
	
	/**
	 * @see ILaunchShortcut#launch(IEditorPart, String)
	 */
	public void launch(IEditorPart editor, String mode) {
		ILaunchShortcut shortcut = getDelegate();
		if (shortcut != null) {
			shortcut.launch(editor, mode);
		}
	}

	/**
	 * @see ILaunchShortcut#launch(ISelection, String)
	 */
	public void launch(ISelection selection, String mode) {
		ILaunchShortcut shortcut = getDelegate();
		if (shortcut != null) {
			shortcut.launch(selection, mode);
		}		
	}
	
	/**
	 * Returns the set of modes this shortcut supports.
	 * 
	 * @return the set of modes this shortcut supports
	 */
	public Set getModes() {
		if (fModes == null) {
			String modes= getConfigurationElement().getAttribute("modes"); //$NON-NLS-1$
			if (modes == null) {
				return new HashSet(0);
			}
			StringTokenizer tokenizer= new StringTokenizer(modes, ","); //$NON-NLS-1$
			fModes = new HashSet(tokenizer.countTokens());
			while (tokenizer.hasMoreTokens()) {
				fModes.add(tokenizer.nextToken().trim());
			}
		}
		return fModes;
	}
	
	/**
	 * Returns the menu path attribute this shortcut, or <code>null</code> if none
	 * 
	 * @return the menu path attribute this shortcut, or <code>null</code> if none
	 * @since 3.0.1
	 */
	public String getMenuPath() {
		return getConfigurationElement().getAttribute("path"); //$NON-NLS-1$
	}	
	
	/*
	 * Only for debugging
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return getId();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPluginContribution#getLocalId()
	 */
	public String getLocalId() {
		return getId();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPluginContribution#getPluginId()
	 */
	public String getPluginId() {
		return fConfig.getDeclaringExtension().getNamespace();
	}
}

