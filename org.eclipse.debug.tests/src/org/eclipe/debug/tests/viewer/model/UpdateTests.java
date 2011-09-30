/*******************************************************************************
 * Copyright (c) 2009, 2011 Wind River Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipe.debug.tests.viewer.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import junit.framework.TestCase;

import org.eclipe.debug.tests.viewer.model.TestModel.TestElement;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IChildrenCountUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IHasChildrenUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IModelDelta;
import org.eclipse.debug.internal.ui.viewers.model.provisional.ITreeModelViewer;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IViewerUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.ModelDelta;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

/**
 * Tests to verify that the viewer property updates following changes in the 
 * model, following simple content update deltas from the model.  
 * 
 * @since 3.6
 */
abstract public class UpdateTests extends TestCase implements ITestModelUpdatesListenerConstants {
    Display fDisplay;
    Shell fShell;
    ITreeModelViewer fViewer;
    TestModelUpdatesListener fListener;
    
    public UpdateTests(String name) {
        super(name);
    }

    /**
     * @throws java.lang.Exception
     */
    protected void setUp() throws Exception {
        fDisplay = PlatformUI.getWorkbench().getDisplay();
        fShell = new Shell(fDisplay);
        fShell.setMaximized(true);
        fShell.setLayout(new FillLayout());

        fViewer = createViewer(fDisplay, fShell);
        
        fListener = new TestModelUpdatesListener(fViewer, false, false);

        fShell.open ();
    }

    abstract protected ITreeModelViewer createViewer(Display display, Shell shell);
    
    /**
     * @throws java.lang.Exception
     */
    protected void tearDown() throws Exception {
        fListener.dispose();
        fViewer.getPresentationContext().dispose();
                
        // Close the shell and exit.
        fShell.close();
        while (!fShell.isDisposed()) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
    }

    protected void runTest() throws Throwable {
        try {
            super.runTest();
        } catch (Throwable t) {
            throw new ExecutionException("Test failed: " + t.getMessage() + "\n fListener = " + fListener.toString(), t);
        }
    }
    
    /**
      * This test:
     * - creates a simple model
     * - replaces the list of elements with a shorter list of elements
     * - refreshes the viewer
     */
    public void testRemoveElements() throws InterruptedException {
        TestModel model = TestModel.simpleSingleLevel();
        fViewer.setAutoExpandLevel(-1);

        // Create the listener
        fListener.reset(TreePath.EMPTY, model.getRootElement(), -1, true, false); 

        // Set the input into the view and update the view.
        fViewer.setInput(model.getRootElement());
        while (!fListener.isFinished()) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
        model.validateData(fViewer, TreePath.EMPTY);
        
        // Update the model
        TestElement root = model.getRootElement();
        TreePath rootPath = new TreePath(new Object[] {});
        TestElement[] newElements = new TestElement[] {
            new TestElement(model, "1", new TestElement[0]),
            new TestElement(model, "2", new TestElement[0]),
            new TestElement(model, "3", new TestElement[0]),
        };
        model.setElementChildren(rootPath, newElements);
        
        // Reset the listener to NOT fail on redundant updates.
        // When elements are remvoed from the model and the model is 
        // refreshed the viewer will issue an IChildrenUpdate for the 
        // missing elements as an optimization.
        fListener.reset(rootPath, root, -1, false, false);
        
        model.postDelta(new ModelDelta(root, IModelDelta.CONTENT));
        while (!fListener.isFinished()) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
        model.validateData(fViewer, TreePath.EMPTY);
    }

    /**
     * This test: 
     * - creates a simple model
     * - sets a list of children to one of the elements
     * - refreshes the viewer
     */
    public void testAddNewChildren() throws InterruptedException {
        TestModel model = TestModel.simpleSingleLevel();
        fViewer.setAutoExpandLevel(-1);

        // Create the listener
        fListener.reset(TreePath.EMPTY, model.getRootElement(), -1, true, false); 

        // Set the input into the view and update the view.
        fViewer.setInput(model.getRootElement());
        while (!fListener.isFinished()) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
        model.validateData(fViewer, TreePath.EMPTY);
        
        // Update the model
        TestElement element = model.getRootElement().getChildren()[0];
        TreePath elementPath = new TreePath(new Object[] { element });
        TestElement[] newChildren = new TestElement[] {
            new TestElement(model, "1.1", new TestElement[0]),
            new TestElement(model, "1.2", new TestElement[0]),
            new TestElement(model, "1.3", new TestElement[0]),
        };
        model.setElementChildren(elementPath, newChildren);

        // Reset the viewer to ignore redundant updates.  The '1' element 
        // will be updated for "hasChildren" before it is expanded, which is 
        // expected.
        TreePath rootPath = TreePath.EMPTY;
        TestElement rootElement = model.getRootElement();
        fListener.reset(rootPath, rootElement, -1, false, false); 

        // Refresh the viewer
        model.postDelta(new ModelDelta(rootElement, IModelDelta.CONTENT));
        while (!fListener.isFinished()) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
        model.validateData(fViewer, TreePath.EMPTY);
    }

    
    private void removeElement(TestModel model, int index, boolean validate) throws InterruptedException {
        ModelDelta delta = model.removeElementChild(TreePath.EMPTY, index);
        
        // Remove delta should generate no new updates, but we still need to wait for the event to
        // be processed.
        fListener.reset();
        model.postDelta(delta);
        while (!fListener.isFinished(MODEL_CHANGED_COMPLETE)) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);

        if (validate) {
            model.validateData(fViewer, TreePath.EMPTY);        
        }
    }
    
    private void addElement(TestModel model, String label, int position, boolean validate) throws InterruptedException {
        ModelDelta delta = model.addElementChild(TreePath.EMPTY, null, position, new TestElement(model, label, new TestElement[0]));
        
        // Remove delta should generate no new updates, but we still need to wait for the event to
        // be processed.
        fListener.reset();
        model.postDelta(delta);
        
        if (validate) {
            while (!fListener.isFinished(MODEL_CHANGED_COMPLETE | CONTENT_UPDATES_COMPLETE | LABEL_UPDATES_COMPLETE)) 
                if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
            model.validateData(fViewer, TreePath.EMPTY);                
        } else {
            while (!fListener.isFinished(MODEL_CHANGED_COMPLETE)) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
        }
    }

    private void insertElement(TestModel model, String label, int position, boolean validate)  throws InterruptedException {
        ModelDelta delta = model.insertElementChild(TreePath.EMPTY, position, new TestElement(model, label, new TestElement[0]));
        
        // Remove delta should generate no new updates, but we still need to wait for the event to
        // be processed.
        fListener.reset();
        model.postDelta(delta);
        
        if (validate) {
            while (!fListener.isFinished(MODEL_CHANGED_COMPLETE | CONTENT_UPDATES_COMPLETE | LABEL_UPDATES_COMPLETE)) 
                if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
            model.validateData(fViewer, TreePath.EMPTY);                
        } else {
            while (!fListener.isFinished(MODEL_CHANGED_COMPLETE)) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
        }
    }

    public void testRepeatedAddRemoveElement() throws InterruptedException {
        //TreeModelViewerAutopopulateAgent autopopulateAgent = new TreeModelViewerAutopopulateAgent(fViewer);

        TestModel model = TestModel.simpleSingleLevel();
        fViewer.setAutoExpandLevel(-1);

        // Create the listener
        fListener.reset(TreePath.EMPTY, model.getRootElement(), -1, false, false); 

        // Set the input into the view and update the view.
        fViewer.setInput(model.getRootElement());
        while (!fListener.isFinished()) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
        model.validateData(fViewer, TreePath.EMPTY);
        
        // Update the model
        removeElement(model, 2, true);
        addElement(model, "3-new", 3, true);
        removeElement(model, 4, true);
        addElement(model, "5-new", 5, true);
        removeElement(model, 1, true);
        addElement(model, "1-new", 1, true);
        removeElement(model, 3, true);
        addElement(model, "4-new", 4, true);
    }


    /**
     * This test case attempts to create a race condition between processing 
     * of the content updates and processing of add/remove model deltas. 
     * <br>
     * See <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=304066">bug 304066</a> 
     */
    public void _X_testContentPlusAddRemoveUpdateRaceConditionsElement() throws InterruptedException {
        TestModel model = TestModel.simpleSingleLevel();
        fViewer.setAutoExpandLevel(-1);

        // Create the listener
        fListener.reset(TreePath.EMPTY, model.getRootElement(), -1, false, false); 

        // Set the input into the view and update the view.
        fViewer.setInput(model.getRootElement());
        while (!fListener.isFinished()) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
        model.validateData(fViewer, TreePath.EMPTY);

        // Create a listener to listen only to a children count update for the root. 
        TestModelUpdatesListener childrenCountUpdateListener = new TestModelUpdatesListener(fViewer, false, false);
        
        for (int i = 0; i < 10; i++) {
            String pass = "pass #" + i;
        
            // Request a content update for view
            childrenCountUpdateListener.reset();
            childrenCountUpdateListener.addChildreCountUpdate(TreePath.EMPTY);
            model.postDelta(new ModelDelta(model.getRootElement(), IModelDelta.CONTENT));
            // Wait until the delta is processed
            while (!fListener.isFinished(MODEL_CHANGED_COMPLETE)) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
            
            removeElement(model, 5, false);
            removeElement(model, 4, false);
            removeElement(model, 3, false);
            removeElement(model, 2, false);
            removeElement(model, 1, false);
            removeElement(model, 0, false);

            // Wait until the children count update is completed using the count from 
            // before elements were removed.
            while (!childrenCountUpdateListener.isFinished(CHILD_COUNT_UPDATES)) 
                if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
            
            insertElement(model, "1 - " + pass, 0, false);
            insertElement(model, "2 - " + pass, 1, false);
            insertElement(model, "3 - " + pass, 2, false);
            insertElement(model, "4 - " + pass, 3, false);
            insertElement(model, "5 - " + pass, 4, false);
            insertElement(model, "6 - " + pass, 5, false);
            
            while (!fListener.isFinished(MODEL_CHANGED_COMPLETE)) 
                if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
            model.validateData(fViewer, TreePath.EMPTY);                

        }
        
        childrenCountUpdateListener.dispose();
    }

    
    /**
     * This test forces the viewer to reschedule pending content updates
     * due to a remove event from the model.
     * @see org.eclipse.debug.internal.ui.viewers.model.TreeModelContentProvider#rescheduleUpdates
     */
    public void testRescheduleUpdates() throws InterruptedException {
        TestModel model = TestModel.simpleSingleLevel();
        fViewer.setAutoExpandLevel(-1);

        // Create the listener
        fListener.reset(TreePath.EMPTY, model.getRootElement(), -1, false, false); 

        // Set the input into the view and update the view.
        fViewer.setInput(model.getRootElement());
        while (!fListener.isFinished()) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
        model.validateData(fViewer, TreePath.EMPTY);

        for (int i = 0; i < 5; i++) {
            // Refresh the viewer so that updates are generated.
            TestElement rootElement = model.getRootElement();
            fListener.reset();
            fListener.addUpdates(TreePath.EMPTY, model.getRootElement(), 1, CHILD_COUNT_UPDATES);
            model.postDelta(new ModelDelta(rootElement, IModelDelta.CONTENT));
    
            // Wait for the delta to be processed.
            while (!fListener.isFinished(MODEL_CHANGED_COMPLETE | CHILD_COUNT_UPDATES)) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
    
            // Update the model
            removeElement(model, 0, true);
            addElement(model, "1", 0, true);
        }
    }

    /**
     * This test forces the viewer to cancel a pending child count update due 
     * to refresh event from model.
     */
    public void testCancelStaleChildrenCountUpdate() throws InterruptedException {
        TestModel model = TestModel.simpleMultiLevel();
        fViewer.setAutoExpandLevel(-1);

        // Create the listener
        fListener.reset(TreePath.EMPTY, model.getRootElement(), -1, false, false); 

        // Set the input into the view and update the view.
        fViewer.setInput(model.getRootElement());
        while (!fListener.isFinished()) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
        model.validateData(fViewer, TreePath.EMPTY);

        for (int i = 0; i < 5; i++) {
            // Refresh the viewer so that updates are generated.
            TestElement rootElement = model.getRootElement();
            fListener.reset();
            model.postDelta(new ModelDelta(rootElement, IModelDelta.CONTENT));
            while (!fListener.isFinished(MODEL_CHANGED_COMPLETE | CHILD_COUNT_UPDATES_STARTED)) 
                if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
        }
    }

    /**
     * This test forces the viewer to cancel updates then process them at once.
     * <p>
     * - Wait until CHILDREN COUNT update started then refresh<br>
     * - Process queued updates in order.<br>
     * </p>
     */
    public void testCanceledUpdates1() throws InterruptedException {
        TestModel model = TestModel.simpleSingleLevel();
        fViewer.setAutoExpandLevel(-1);

        // Create the listener
        fListener.reset(TreePath.EMPTY, model.getRootElement(), -1, false, false); 

        // Set the input into the view and update the view.
        fViewer.setInput(model.getRootElement());
        while (!fListener.isFinished()) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
        model.validateData(fViewer, TreePath.EMPTY);

        
        model.setQeueueingUpdate(true);
        
        for (int i = 0; i < 5; i++) {
            // Refresh the viewer so that updates are generated.
            fListener.reset();
            model.postDelta(new ModelDelta(model.getRootElement(), IModelDelta.CONTENT));
    
            // Wait for the delta to be processed.
            while (!fListener.isFinished(MODEL_CHANGED_COMPLETE | CHILD_COUNT_UPDATES_STARTED)) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
    
        }
        
        model.setQeueueingUpdate(false);
        while (!fListener.isFinished(ALL_UPDATES_COMPLETE)) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
        
    }
    
    /**
     * This test forces the viewer to cancel updates then process them at once.
     * <p>
     * - Wait until CHILDREN COUNT update started then refresh<br>
     * - Process queued updates in REVERSE order.<br>
     * </p>
     */
    public void testCanceledUpdates2() throws InterruptedException {
        TestModel model = TestModel.simpleSingleLevel();
        fViewer.setAutoExpandLevel(-1);

        // Create the listener
        fListener.reset(TreePath.EMPTY, model.getRootElement(), -1, false, false); 

        // Set the input into the view and update the view.
        fViewer.setInput(model.getRootElement());
        while (!fListener.isFinished()) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
        model.validateData(fViewer, TreePath.EMPTY);

        
        model.setQeueueingUpdate(true);
        
        for (int i = 0; i < 5; i++) {
            // Refresh the viewer so that updates are generated.
            fListener.reset();
            model.postDelta(new ModelDelta(model.getRootElement(), IModelDelta.CONTENT));
    
            // Wait for the delta to be processed.
            while (!fListener.isFinished(MODEL_CHANGED_COMPLETE | CHILD_COUNT_UPDATES_STARTED)) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
    
        }
        
        List updates = new ArrayList(model.getQueuedUpdates());
        model.getQueuedUpdates().clear();
        for (int i = updates.size() - 1; i >= 0; i--) {
            model.processUpdate((IViewerUpdate)updates.get(i));
        }
        
        model.setQeueueingUpdate(false);
        while (!fListener.isFinished(ALL_UPDATES_COMPLETE)) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
    }
    
    /**
     * This test forces the viewer to cancel updates then process them at once.
     * <p>
     * - Wait until CHILDREN update started then refresh<br>
     * - Process queued updates in order.<br>
     * </p>
     */
    public void testCanceledUpdates3() throws InterruptedException {
        TestModel model = TestModel.simpleSingleLevel();
        fViewer.setAutoExpandLevel(-1);

        // Create the listener
        fListener.reset(TreePath.EMPTY, model.getRootElement(), -1, false, false); 

        // Set the input into the view and update the view.
        fViewer.setInput(model.getRootElement());
        while (!fListener.isFinished()) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
        model.validateData(fViewer, TreePath.EMPTY);

        
        model.setQeueueingUpdate(true);
        
        for (int i = 0; i < 5; i++) {
            // Refresh the viewer so that updates are generated.
            fListener.reset();
            model.postDelta(new ModelDelta(model.getRootElement(), IModelDelta.CONTENT));
    
            // Wait for the delta to be processed.
            // Wait for the delta to be processed.
            while (!fListener.isFinished(MODEL_CHANGED_COMPLETE | CHILDREN_UPDATES_STARTED)) {
                completeQueuedUpdatesOfType(model, IChildrenCountUpdate.class);
                completeQueuedUpdatesOfType(model, IHasChildrenUpdate.class);
                if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
            }
        }
        
        model.setQeueueingUpdate(false);
        while (!fListener.isFinished(ALL_UPDATES_COMPLETE)) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
        
    }
    
    /**
     * This test forces the viewer to cancel updates then process them at once.
     * <p>
     * - Wait until CHILDREN update started then refresh<br>
     * - Process queued updates in REVERSE order.<br>
     * </p>
     */
    public void testCanceledUpdates4() throws InterruptedException {
        TestModel model = TestModel.simpleSingleLevel();
        fViewer.setAutoExpandLevel(-1);

        // Create the listener
        fListener.reset(TreePath.EMPTY, model.getRootElement(), -1, false, false); 

        // Set the input into the view and update the view.
        fViewer.setInput(model.getRootElement());
        while (!fListener.isFinished()) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
        model.validateData(fViewer, TreePath.EMPTY);

        
        model.setQeueueingUpdate(true);
        
        for (int i = 0; i < 5; i++) {
            // Refresh the viewer so that updates are generated.
            fListener.reset();
            model.postDelta(new ModelDelta(model.getRootElement(), IModelDelta.CONTENT));
    
            // Wait for the delta to be processed.
            while (!fListener.isFinished(MODEL_CHANGED_COMPLETE | CHILDREN_UPDATES_STARTED)) {
                completeQueuedUpdatesOfType(model, IChildrenCountUpdate.class);
                completeQueuedUpdatesOfType(model, IHasChildrenUpdate.class);
                if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
            }
    
        }
        
        List updates = new ArrayList(model.getQueuedUpdates());
        model.getQueuedUpdates().clear();
        for (int i = updates.size() - 1; i >= 0; i--) {
            model.processUpdate((IViewerUpdate)updates.get(i));
        }
        
        model.setQeueueingUpdate(false);
        while (!fListener.isFinished(ALL_UPDATES_COMPLETE)) if (!fDisplay.readAndDispatch ()) Thread.sleep(0);
    }
    
    private void completeQueuedUpdatesOfType(TestModel model, Class updateClass) {
        List updatesToComplete = new LinkedList();
        
        for (Iterator itr = model.getQueuedUpdates().iterator(); itr.hasNext();) {
            IViewerUpdate update = (IViewerUpdate)itr.next();
            if (updateClass.isInstance(update)) {
                updatesToComplete.add(update);
                itr.remove();
            }
        }        
        if (updatesToComplete != null) {
            for (Iterator itr = updatesToComplete.iterator(); itr.hasNext();) {
                model.processUpdate((IViewerUpdate)itr.next());
            }
        }
    }
    
}
