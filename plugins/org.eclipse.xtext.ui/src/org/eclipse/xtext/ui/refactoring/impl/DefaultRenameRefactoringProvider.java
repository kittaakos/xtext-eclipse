/*******************************************************************************
 * Copyright (c) 2010 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.ui.refactoring.impl;

import org.eclipse.emf.common.util.URI;
import org.eclipse.ltk.core.refactoring.RefactoringTickProvider;
import org.eclipse.ltk.core.refactoring.participants.RenameRefactoring;
import org.eclipse.xtext.ui.refactoring.IRenameRefactoringProvider;

import com.google.inject.Inject;
import com.google.inject.Provider;

public class DefaultRenameRefactoringProvider implements IRenameRefactoringProvider {
	@Inject
	private Provider<AbstractRenameProcessor> processorProvider;

	public RenameRefactoring getRenameRefactoring(URI targetElementURI) {
		AbstractRenameProcessor processor = processorProvider.get();
		if (processor != null) {
			processor.initialize(targetElementURI);
			return new RenameRefactoring(processor) {
				@Override
				protected RefactoringTickProvider doGetRefactoringTickProvider() {
					return new RefactoringTickProvider(1, 200, 1, 1);
				}
			};
		}
		return null;
	}

}