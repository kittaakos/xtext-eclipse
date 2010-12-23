/*******************************************************************************
 * Copyright (c) 2010 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.ui.tests.refactoring;

import static org.eclipse.xtext.ui.junit.util.IResourcesSetupUtil.*;
import static org.eclipse.xtext.ui.junit.util.JavaProjectSetupUtil.*;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.xtext.ui.XtextProjectHelper;
import org.eclipse.xtext.ui.editor.XtextEditor;
import org.eclipse.xtext.ui.junit.util.IResourcesSetupUtil;
import org.eclipse.xtext.ui.refactoring.impl.RenameElementProcessor;
import org.eclipse.xtext.ui.tests.Activator;
import org.eclipse.xtext.ui.tests.editor.AbstractEditorTest;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;

/**
 * @author koehnlein - Initial contribution and API
 */
public class RenameRefactoringIntegrationTest extends AbstractEditorTest {

	@Inject
	private Provider<RenameElementProcessor> processorProvider;

	private static final String TEST_PROJECT = "refactoring.test";
	private static final String TEST_FILE0_NAME = TEST_PROJECT + "/" + "File0.refactoringtestlanguage";
	private static final String TEST_FILE1_NAME = TEST_PROJECT + "/" + "File1.refactoringtestlanguage";
	private String initialModel0;
	private IFile testFile0;
	private String initialModel1;
	private IFile testFile1;
	private URI uriB;
	private IProject project;
	protected Change undoChange;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		project = createProject(TEST_PROJECT);
		IJavaProject javaProject = makeJavaProject(project);
		addNature(javaProject.getProject(), XtextProjectHelper.NATURE_ID);
		Injector injector = Activator.getInstance().getInjector(getEditorId());
		injector.injectMembers(this);
		initialModel0 = "B A { ref B }";
		testFile0 = IResourcesSetupUtil.createFile(TEST_FILE0_NAME, initialModel0);
		initialModel1 = "X { ref B }";
		testFile1 = IResourcesSetupUtil.createFile(TEST_FILE1_NAME, initialModel1);
		uriB = URI.createPlatformResourceURI(testFile0.getFullPath().toString(), true).appendFragment("//@elements.0");
	}

	@Override
	protected String getEditorId() {
		return "org.eclipse.xtext.ui.tests.refactoring.RefactoringTestLanguage";
	}

	public void testFileFileRename() throws Exception {
		doRename();
		assertEquals(initialModel0.replaceAll("B", "C"), readFile(testFile0));
		assertEquals(initialModel1.replaceAll("B", "C"), readFile(testFile1));
		undoRename();
		assertEquals(initialModel0, readFile(testFile0));
		assertEquals(initialModel1, readFile(testFile1));
	}

	public void testEditorFileRename() throws Exception {
		XtextEditor editor = openEditor(testFile0);
		assertFalse(editor.isDirty());
		doRename();
		assertTrue(editor.isDirty());
		assertEquals(initialModel0.replaceAll("B", "C"), editor.getDocument().get());	
		assertEquals(initialModel1.replaceAll("B", "C"), readFile(testFile1));
		undoRename();
		assertTrue(editor.isDirty());
		assertEquals(initialModel0, editor.getDocument().get());
		assertEquals(initialModel1, readFile(testFile1));
	}
	
	public void testEditorEditorRename() throws Exception {
		XtextEditor editor0 = openEditor(testFile0);
		XtextEditor editor1 = openEditor(testFile1);
		assertFalse(editor0.isDirty());
		assertFalse(editor1.isDirty());
		doRename();
		assertTrue(editor0.isDirty());
		assertTrue(editor1.isDirty());
		assertEquals(initialModel0.replaceAll("B", "C"), editor0.getDocument().get());	
		assertEquals(initialModel1.replaceAll("B", "C"), editor1.getDocument().get());	
		undoRename();
		assertTrue(editor0.isDirty());
		assertTrue(editor1.isDirty());
		assertEquals(initialModel0, editor0.getDocument().get());
		assertEquals(initialModel1, editor1.getDocument().get());
	}
	
	public void testFileEditorRename() throws Exception {
		XtextEditor editor1 = openEditor(testFile1);
		assertFalse(editor1.isDirty());
		doRename();
		assertEquals(initialModel0.replaceAll("B", "C"), readFile(testFile0));
		assertTrue(editor1.isDirty());
		assertEquals(initialModel1.replaceAll("B", "C"), editor1.getDocument().get());	
		undoRename();
		assertEquals(initialModel0, readFile(testFile0));
		assertTrue(editor1.isDirty());
		assertEquals(initialModel1, editor1.getDocument().get());
	}
	
	public void testDirtyEditor() throws Exception {
		XtextEditor editor = openEditor(testFile0);
		String dirtyModel = "Y B A { ref B }";
		editor.getDocument().set(dirtyModel);
		assertTrue(editor.isDirty());
		uriB = URI.createPlatformResourceURI(testFile0.getFullPath().toString(), true).appendFragment("//@elements.1");
		doRename();
		assertTrue(editor.isDirty());
		assertEquals(dirtyModel.replaceAll("B", "C"),editor.getDocument().get());
	}
	
	public void testRefFromOtherLanguage() throws Exception {
		String initialModel = "ref B";
		IFile otherLanguageFile = IResourcesSetupUtil.createFile(TEST_PROJECT + "/otherLanguageFile.referringtestlanguage", initialModel);
		doRename();
		assertEquals(initialModel.replaceAll("B", "C"), readFile(otherLanguageFile));
	}
	
	protected void doRename() throws Exception {
		IResourcesSetupUtil.waitForAutoBuild();
		final Change change = createChange(uriB, "C");
		new WorkspaceModifyOperation() {
			@Override
			protected void execute(IProgressMonitor monitor) throws CoreException, InvocationTargetException,
					InterruptedException {
				undoChange = change.perform(monitor);
			}
		}.run(null);
	}

	protected void undoRename() throws Exception{
		IResourcesSetupUtil.waitForAutoBuild();
		new WorkspaceModifyOperation() {
			@Override
			protected void execute(IProgressMonitor monitor) throws CoreException, InvocationTargetException,
					InterruptedException {
				undoChange.perform(monitor);
			}
		}.run(null);
	}
	
	protected Change createChange(URI targetElementURI, String newName) throws Exception {
		RenameElementProcessor processor = processorProvider.get();
		processor.initialize(targetElementURI);
		RefactoringStatus initialStatus = processor.checkInitialConditions(new NullProgressMonitor());
		assertTrue(initialStatus.isOK());
		processor.setNewName(newName);
		RefactoringStatus finalStatus = processor.checkFinalConditions(new NullProgressMonitor(), null);
		assertTrue(finalStatus.isOK());
		final Change change = processor.createChange(new NullProgressMonitor());
		assertNotNull(change);
		return change;
	}	
	
	protected String readFile(IFile file) throws Exception {
		InputStream inputStream = file.getContents();
		try {
			byte[] buffer = new byte[2048];
			int bytesRead = 0;
			StringBuffer b = new StringBuffer();
			do {
				bytesRead = inputStream.read(buffer);
				if (bytesRead != -1)
					b.append(new String(buffer, 0, bytesRead));
			} while (bytesRead != -1);
			return b.toString();
		} finally {
			inputStream.close();
		}
	}

}
