/*
 * Copyright (c) 2020, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ballerinalang.langserver.codeaction.providers.openapi.ballerinatoopenapi;

import org.ballerinalang.annotation.JavaSPIService;
import org.ballerinalang.langserver.codeaction.providers.AbstractCodeActionProvider;
import org.ballerinalang.langserver.command.executors.openapi.ballerinatoopenapi.CreateBallerinaServiceResourceExecutor;
import org.ballerinalang.langserver.common.constants.CommandConstants;
import org.ballerinalang.langserver.common.utils.CommonUtil;
import org.ballerinalang.langserver.commons.LSContext;
import org.ballerinalang.langserver.commons.codeaction.CodeActionNodeType;
import org.ballerinalang.langserver.commons.command.CommandArgument;
import org.ballerinalang.langserver.commons.workspace.LSDocumentIdentifier;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentManager;
import org.ballerinalang.langserver.compiler.DocumentServiceKeys;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;

import static org.ballerinalang.langserver.common.constants.CommandConstants.CREATE_SERVICE_RESOURCE_METHOD;

/**
 * Code Action provider for open api service resource implement.
 *
 * @since 1.2.0
 */
@JavaSPIService("org.ballerinalang.langserver.commons.codeaction.spi.LSCodeActionProvider")
public class CreateBallerinaServiceResourceCodeAction extends AbstractCodeActionProvider {
    private static final String CONTAINS_RESOURCE_NOT_DOCUMENTED =
            "Ballerina service contains a Resource that is not documented in the OpenAPI contract.";

    @Override
    public List<CodeAction> getNodeBasedCodeActions(CodeActionNodeType nodeType, LSContext lsContext,
                                                    List<Diagnostic> allDiagnostics) {
        return null;
    }

    @Override
    public List<CodeAction> getDiagBasedCodeActions(CodeActionNodeType nodeType, LSContext lsContext,
                                                    List<Diagnostic> diagnosticsOfRange,
                                                    List<Diagnostic> allDiagnostics) {
        WorkspaceDocumentManager documentManager = lsContext.get(DocumentServiceKeys.DOC_MANAGER_KEY);
        Optional<Path> filePath = CommonUtil.getPathFromURI(lsContext.get(DocumentServiceKeys.FILE_URI_KEY));
        LSDocumentIdentifier document = null;
        try {
            document = documentManager.getLSDocument(filePath.get());
        } catch (WorkspaceDocumentException e) {
            // ignore
        }
        List<CodeAction> actions = new ArrayList<>();

        if (document == null) {
            return actions;
        }
        for (Diagnostic diagnostic : diagnosticsOfRange) {
            if (diagnostic.getMessage().startsWith(CONTAINS_RESOURCE_NOT_DOCUMENTED)) {
                CodeAction codeAction = getCommand(document, diagnostic, lsContext);
                if (codeAction != null) {
                    actions.add(codeAction);
                }
            }
        }
        return actions;
    }

    private static CodeAction getCommand(LSDocumentIdentifier document, Diagnostic diagnostic,
                                         LSContext lsContext) {
        String diagnosticMessage = diagnostic.getMessage();
        Position position = diagnostic.getRange().getStart();
        int line = position.getLine();
        int column = position.getCharacter();
        String uri = lsContext.get(DocumentServiceKeys.FILE_URI_KEY);
        CommandArgument lineArg = new CommandArgument(CommandConstants.ARG_KEY_NODE_LINE, "" + line);
        CommandArgument colArg = new CommandArgument(CommandConstants.ARG_KEY_NODE_COLUMN, "" + column);
        CommandArgument uriArg = new CommandArgument(CommandConstants.ARG_KEY_DOC_URI, uri);
        List<Diagnostic> diagnostics = new ArrayList<>();

        Matcher matcher = CommandConstants.RESOURCE_METHOD_NOT_FOUND_IN_OPENAPI.matcher(diagnosticMessage);
        if (matcher.find() && matcher.groupCount() > 0) {
            String path = matcher.group(1);
            String commandTitle = String.format(CommandConstants.CREATE_SERVICE_RESOURCE_METHOD_IN_OPENAPI, path);
            CommandArgument pathArg = new CommandArgument(CommandConstants.ARG_KEY_PATH, path);

            List<Object> args = Arrays.asList(lineArg, colArg, uriArg, pathArg);
            CodeAction action = new CodeAction(commandTitle);
            action.setKind(CodeActionKind.QuickFix);
            action.setCommand(
                    new Command(CREATE_SERVICE_RESOURCE_METHOD, CreateBallerinaServiceResourceExecutor.COMMAND,
                                args));
            action.setDiagnostics(diagnostics);
            return action;
        }
        return null;
    }

}
