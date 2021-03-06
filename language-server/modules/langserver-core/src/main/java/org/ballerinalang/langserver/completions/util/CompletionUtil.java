/*
 * Copyright (c) 2018, WSO2 Inc. (http://wso2.com) All Rights Reserved.
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
package org.ballerinalang.langserver.completions.util;

import io.ballerinalang.compiler.syntax.tree.BasicLiteralNode;
import io.ballerinalang.compiler.syntax.tree.ModulePartNode;
import io.ballerinalang.compiler.syntax.tree.Node;
import io.ballerinalang.compiler.syntax.tree.NonTerminalNode;
import io.ballerinalang.compiler.syntax.tree.SyntaxKind;
import io.ballerinalang.compiler.syntax.tree.SyntaxTree;
import io.ballerinalang.compiler.syntax.tree.Token;
import io.ballerinalang.compiler.text.LinePosition;
import io.ballerinalang.compiler.text.TextDocument;
import org.ballerinalang.langserver.common.utils.CommonUtil;
import org.ballerinalang.langserver.commons.LSContext;
import org.ballerinalang.langserver.commons.completion.CompletionKeys;
import org.ballerinalang.langserver.commons.completion.LSCompletionException;
import org.ballerinalang.langserver.commons.completion.LSCompletionItem;
import org.ballerinalang.langserver.commons.completion.spi.CompletionProvider;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentManager;
import org.ballerinalang.langserver.compiler.DocumentServiceKeys;
import org.ballerinalang.langserver.completions.ProviderFactory;
import org.ballerinalang.langserver.completions.TreeVisitor;
import org.ballerinalang.langserver.completions.sourceprune.CompletionsTokenTraverserFactory;
import org.ballerinalang.langserver.completions.util.sorters.ItemSorters;
import org.ballerinalang.langserver.sourceprune.SourcePruner;
import org.ballerinalang.langserver.sourceprune.TokenTraverserFactory;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Common utility methods for the completion operation.
 */
public class CompletionUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompletionUtil.class);

    /**
     * Resolve the visible symbols from the given BLang Package and the current context.
     *
     * @param completionContext Completion Service Context
     */
    public static void resolveSymbols(LSContext completionContext) {
        // Visit the package to resolve the symbols
        TreeVisitor treeVisitor = new TreeVisitor(completionContext);
        BLangPackage bLangPackage = completionContext.get(DocumentServiceKeys.CURRENT_BLANG_PACKAGE_CONTEXT_KEY);
        bLangPackage.accept(treeVisitor);
    }

    /**
     * Get the completion Items for the context.
     *
     * @param ctx Completion context
     * @return {@link List}         List of resolved completion Items
     */
    public static List<CompletionItem> getCompletionItems(LSContext ctx)
            throws WorkspaceDocumentException, LSCompletionException {
        fillTokenInfoAtCursor(ctx);
        NonTerminalNode nodeAtCursor = ctx.get(CompletionKeys.NODE_AT_CURSOR_KEY);

        List<LSCompletionItem> items = route(ctx, nodeAtCursor);
        return getPreparedCompletionItems(ctx, items);
    }

    /**
     * Get the nearest matching provider for the context node.
     *
     * @param node node to evaluate
     * @return {@link Optional} provider which resolved
     */
    public static List<LSCompletionItem> route(LSContext ctx, Node node)
            throws LSCompletionException {
        List<LSCompletionItem> completionItems = new ArrayList<>();
        if (node == null) {
            return completionItems;
        }
        Map<Class<?>, CompletionProvider<Node>> providers = ProviderFactory.instance().getProviders();
        Node reference = node;
        CompletionProvider<Node> provider = null;

        while ((reference != null)) {
            provider = providers.get(reference.getClass());
            if (provider != null) {
                break;
            }
            reference = reference.parent();
        }

        if (provider == null) {
            return completionItems;
        }
        return provider.getCompletions(ctx, reference);
    }

    private static List<CompletionItem> getPreparedCompletionItems(LSContext context, List<LSCompletionItem> items) {
        List<CompletionItem> completionItems = new ArrayList<>();
        boolean isSnippetSupported = context.get(CompletionKeys.CLIENT_CAPABILITIES_KEY).getCompletionItem()
                .getSnippetSupport();
        List<CompletionItem> sortedItems = ItemSorters.get(context.get(CompletionKeys.SCOPE_NODE_KEY).getClass())
                .sortItems(context, items);

        // TODO: Remove this
        for (CompletionItem item : sortedItems) {
            if (!isSnippetSupported) {
                item.setInsertText(CommonUtil.getPlainTextSnippet(item.getInsertText()));
                item.setInsertTextFormat(InsertTextFormat.PlainText);
            } else {
                item.setInsertTextFormat(InsertTextFormat.Snippet);
            }
            completionItems.add(item);
        }

        return completionItems;
    }

    /**
     * Prune source if syntax errors exists.
     *
     * @param lsContext {@link LSContext}
     * @throws SourcePruneException       when file uri is invalid
     * @throws WorkspaceDocumentException when document read error occurs
     */
    @Deprecated
    public static void pruneSource(LSContext lsContext) throws SourcePruneException, WorkspaceDocumentException {
        WorkspaceDocumentManager documentManager = lsContext.get(DocumentServiceKeys.DOC_MANAGER_KEY);
        String uri = lsContext.get(DocumentServiceKeys.FILE_URI_KEY);
        if (uri == null) {
            throw new SourcePruneException("fileUri cannot be null!");
        }

        Path filePath = Paths.get(URI.create(uri));
        TokenTraverserFactory tokenTraverserFactory = new CompletionsTokenTraverserFactory(filePath, documentManager,
                SourcePruner.newContext());
        SourcePruner.pruneSource(lsContext, tokenTraverserFactory);

        // Update document manager
        documentManager.setPrunedContent(filePath, tokenTraverserFactory.getTokenStream().getText());
    }

    /**
     * Find the token at cursor.
     *
     * @throws WorkspaceDocumentException while retrieving the syntax tree from the document manager
     */
    public static void fillTokenInfoAtCursor(LSContext context) throws WorkspaceDocumentException {
        WorkspaceDocumentManager docManager = context.get(DocumentServiceKeys.DOC_MANAGER_KEY);
        Optional<Path> filePath = CommonUtil.getPathFromURI(context.get(DocumentServiceKeys.FILE_URI_KEY));
        if (!filePath.isPresent()) {
            return;
        }
        SyntaxTree syntaxTree = docManager.getTree(filePath.get());
        TextDocument textDocument = syntaxTree.textDocument();

        Position position = context.get(DocumentServiceKeys.POSITION_KEY).getPosition();
        int txtPos = textDocument.textPositionFrom(LinePosition.from(position.getLine(), position.getCharacter()));
        Token tokenAtCursor = ((ModulePartNode) syntaxTree.rootNode()).findToken(txtPos);

        context.put(CompletionKeys.TOKEN_AT_CURSOR_KEY, tokenAtCursor);
        context.put(CompletionKeys.NODE_AT_CURSOR_KEY, getNodeAtCursor(tokenAtCursor, context));
    }

    private static NonTerminalNode getNodeAtCursor(Token tokenAtCursor, LSContext context) {
        NonTerminalNode parent = tokenAtCursor.parent();
        int cursorLine = context.get(DocumentServiceKeys.POSITION_KEY).getPosition().getLine();

        while (parent.kind() == SyntaxKind.QUALIFIED_NAME_REFERENCE
                || parent.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE
                || parent.kind() == SyntaxKind.NIL_LITERAL
                || parent instanceof BasicLiteralNode
                || cursorLine < parent.lineRange().startLine().line()) {
            parent = parent.parent();
        }

        return parent;
    }
}
