/*******************************************************************************
 * Copyright (c) 2005, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Bjorn Freeman-Benson - initial API and implementation
 *     Pawel Piech (Wind River) - ported PDA Virtual Machine to Java (Bug 261400)
 *******************************************************************************/
package org.eclipse.debug.examples.core.pda.model;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.debug.examples.core.pda.protocol.PDAChildrenCommand;
import org.eclipse.debug.examples.core.pda.protocol.PDAListResult;

/**
 * Value of a PDA variable.
 */
public class PDAValue extends PDADebugElement implements IValue {
	
    final private PDAVariable fVariable;
	final private String fValue;
	
	public PDAValue(PDAVariable variable, String value) {
		super(variable.getStackFrame().getPDADebugTarget());
		fVariable = variable;
		fValue = value;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IValue#getReferenceTypeName()
	 */
	public String getReferenceTypeName() throws DebugException {
		try {
			Integer.parseInt(fValue);
		} catch (NumberFormatException e) {
			return "text";
		}
		return "integer";
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IValue#getValueString()
	 */
	public String getValueString() throws DebugException {
		return fValue;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IValue#isAllocated()
	 */
	public boolean isAllocated() throws DebugException {
		return true;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IValue#getVariables()
	 */
	public IVariable[] getVariables() throws DebugException {
        //#ifdef ex_ec2009
	    // TODO Excercise 4
	    // Calculate and create the variable objects representing the children
	    // of this value.  
	    // 
	    // The PDA debugger has a dedicated command for retrieving variable 
	    // children: PDAChildrenCommand.  Send this command to the debugger
	    // engine using PDADebugElement.sendCommand(), and based on the results
	    // create an array of child PDAVariable objects.
//#	    return new IVariable[0];
	    //#else
	    PDAStackFrame frame = fVariable.getStackFrame();
	    PDAListResult result =  (PDAListResult) sendCommand(
	        new PDAChildrenCommand(frame.getThreadIdentifier(), frame.getIdentifier(), fVariable.getName()) );
	    
	    IVariable[] children = new IVariable[result.fValues.length];
	    for(int i = 0; i < result.fValues.length; i++) {
	        children[i] = new PDAVariable(frame, result.fValues[i]);
	    }
		return children;
		//#endif
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IValue#hasVariables()
	 */
	public boolean hasVariables() throws DebugException {
	    return getVariables().length != 0;
	}
	/*
	 *  (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
    public boolean equals(Object obj) {
        return obj instanceof PDAValue && ((PDAValue)obj).fValue.equals(fValue);
    }
    /*
     *  (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
        return fValue.hashCode();
    }
    
    /**
     * Returns the variable that this value was created for.
     * 
     * @return The variable that this value was created for.
     * 
     * @since 3.5
     */
    public PDAVariable getVariable() {
        return fVariable;
    }
}
