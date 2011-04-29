/*******************************************************************************
 * Copyright (c) 2011 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.ui.refactoring.ui;

import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.ui.IEditorPart;
import org.eclipse.xtext.resource.IGlobalServiceProvider;
import org.eclipse.xtext.ui.editor.XtextEditor;
import org.eclipse.xtext.ui.refactoring.IRenameProcessorAdapter;
import org.eclipse.xtext.ui.refactoring.IRenameRefactoringProvider;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/**
 * @author koehnlein - Initial contribution and API
 */
@Singleton
public class RenameRefactoringController {

	private static final Logger LOG = Logger.getLogger(RenameRefactoringController.class);

	@Inject
	private Provider<RenameLinkedMode> renameLinkedModeProvider;

	@Inject
	private IGlobalServiceProvider globalServiceProvider;

	@Inject
	private IRenameProcessorAdapter.Factory renameProcessorAdapterFactory;

	/**
	 * Factory for components from the language holding the declaration of the renamed element.
	 * 
	 * @since 2.0
	 */
	protected static class DeclaringLanguageComponentFactory {
		@Inject
		private Provider<RenameRefactoringExecuter> renameRefactoringExecuterProvider;

		public RenameRefactoringExecuter createRenameRefactoringExecuter() {
			return renameRefactoringExecuterProvider.get();
		}

		@Inject
		private IRenameRefactoringProvider renameRefactoringProvider;

		public ProcessorBasedRefactoring createRenameRefactoring(IRenameElementContext renameElementContext) {
			return renameRefactoringProvider.getRenameRefactoring(renameElementContext);
		}
	}

	private DeclaringLanguageComponentFactory declaringLanguage;

	private RenameLinkedMode activeLinkedMode;

	private IRenameElementContext renameElementContext;
	private ProcessorBasedRefactoring renameRefactoring;
	private IRenameProcessorAdapter renameProcessorAdapter;

	private LinkedEditingUndoSupport undoSupport;

	public void initialize(IRenameElementContext renameElementContext) {
		try {
			declaringLanguage = globalServiceProvider.findService(renameElementContext.getTargetElementURI(),
					DeclaringLanguageComponentFactory.class);
		} catch (Exception e) {
			LOG.error("Error getting refactoring components from declaring language", e);
			throw new WrappedException(e);
		}
		this.renameElementContext = renameElementContext;
		renameRefactoring = declaringLanguage.createRenameRefactoring(renameElementContext);
		renameProcessorAdapter = renameProcessorAdapterFactory.create(renameRefactoring);
	}

	public void startRefactoring(RefactoringType refactoringType) {
		if (refactoringType == RefactoringType.LINKED_EDITING) {
			startLinkedEditing();
			return;
		}
		ViewFreezer freezer = new ViewFreezer(getXtextEditor().getInternalSourceViewer());
		try {
			freezer.freeze();
			// Cancel undoable right now because the freezer would show the old
			// state and not the new one
			undoSupport.undoDocumentChanges();
			undoSupport = null;
			cancelLinkedMode();
			switch (refactoringType) {
				case REFACTORING_DIRECT:
					startDirectRefactoring();
					break;
				case REFACTORING_DIALOG:
					startRefactoringWithDialog(false);
					break;
				case REFACTORING_PREVIEW:
					startRefactoringWithDialog(true);
					break;
				default:
					throw new IllegalStateException("Invalid refactoring type " + refactoringType.toString());
			}
		} catch (InterruptedException exc) {
			// canceling by the user is ok
			restoreOriginalSelection();
		} finally {
			freezer.release();
		}
	}

	protected void startLinkedEditing() {
		if (activeLinkedMode != null) {
			if (activeLinkedMode.isSameRenameElementContext(renameElementContext)) {
				startRefactoring(RefactoringType.REFACTORING_DIALOG);
				return;
			} else {
				cancelLinkedMode();
			}
		}
		activeLinkedMode = renameLinkedModeProvider.get();
		activeLinkedMode.start(renameElementContext);
		undoSupport = new LinkedEditingUndoSupport(getXtextEditor());
	}

	public RenameLinkedMode getActiveLinkedMode() {
		return activeLinkedMode;
	}

	/**
	 * Linked mode is unrecoverable canceled.
	 */
	public void cancelLinkedMode() {
		if (activeLinkedMode != null) {
			activeLinkedMode.linkedModeLeft();
			activeLinkedMode = null;
		}
	}

	protected void startDirectRefactoring() throws InterruptedException {
		if (renameProcessorAdapter.getNewName() == null) {
			restoreOriginalSelection();
		} else {
			RenameRefactoringExecuter renameRefactoringExecuter = declaringLanguage.createRenameRefactoringExecuter();
			renameRefactoringExecuter.execute(getXtextEditor(), renameRefactoring);
		}
	}

	protected void startRefactoringWithDialog(final boolean previewOnly) throws InterruptedException {
		RenameElementWizard renameElementWizard = new RenameElementWizard(renameRefactoring, renameProcessorAdapter) {
			@Override
			protected void addUserInputPages() {
				if (!previewOnly) {
					super.addUserInputPages();
				}
			}
		};
		if (previewOnly) {
			renameElementWizard.setForcePreviewReview(true);
		}
		RefactoringWizardOpenOperation openOperation = new RefactoringWizardOpenOperation(renameElementWizard);
		openOperation.run(renameElementContext.getTriggeringEditor().getSite().getShell(), "Rename Element");
	}

	protected void restoreOriginalSelection() {
		ISelection originalSelection = renameElementContext.getTriggeringEditorSelection();
		if (originalSelection instanceof ITextSelection) {
			getXtextEditor().getInternalSourceViewer().setSelectedRange(
					((ITextSelection) originalSelection).getOffset(), ((ITextSelection) originalSelection).getLength());
		}
	}

	public boolean updateNewName(String newName) {
		if (isInValid(newName))
			return false;
		renameProcessorAdapter.setNewName(newName);
		return true;
	}

	protected boolean isInValid(String newName) {
		if (newName != null && newName.length() > 0) {
			RefactoringStatus result = renameProcessorAdapter.validateNewName(newName);
			return result.hasWarning();
		} else {
			return false;
		}
	}

	protected XtextEditor getXtextEditor() {
		IEditorPart part = renameElementContext.getTriggeringEditor();
		if (part instanceof XtextEditor) {
			return (XtextEditor) part;
		}
		return null;
	}
}
