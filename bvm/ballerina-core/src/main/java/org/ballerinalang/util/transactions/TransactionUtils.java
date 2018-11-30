/*
*  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/
package org.ballerinalang.util.transactions;

import org.ballerinalang.bre.bvm.BVMExecutor;
import org.ballerinalang.bre.bvm.Strand;
import org.ballerinalang.model.types.TypeTags;
import org.ballerinalang.model.values.BBoolean;
import org.ballerinalang.model.values.BError;
import org.ballerinalang.model.values.BInteger;
import org.ballerinalang.model.values.BString;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.util.codegen.FunctionInfo;
import org.ballerinalang.util.codegen.PackageInfo;
import org.ballerinalang.util.exceptions.BallerinaException;

/**
 * Utility methods used in transaction handling.
 *
 * @since 0.970.0
 */
public class TransactionUtils {

    public static BValue[] notifyTransactionBegin(Strand ctx, String globalTransactionId, String url,
                                                  int transactionBlockId, String protocol) {
        BValue[] args = {
                (globalTransactionId == null ? null : new BString(globalTransactionId)),
                new BInteger(transactionBlockId), new BString(url),
                new BString(protocol)
        };
        BValue[] returns = invokeCoordinatorFunction(ctx, TransactionConstants.COORDINATOR_BEGIN_TRANSACTION, args);
        checkTransactionCoordinatorError(returns[0], ctx, "error in transaction start: ");
        return returns;
    }

    public static void notifyTransactionEnd(Strand ctx, String globalTransactionId,
            int transactionBlockId) {
        BValue[] args = {new BString(globalTransactionId), new BInteger(transactionBlockId)};
        BValue[] returns = invokeCoordinatorFunction(ctx, TransactionConstants.COORDINATOR_END_TRANSACTION, args);
        checkTransactionCoordinatorError(returns[0], ctx, "error in transaction end: ");
    }

    public static void notifyTransactionAbort(Strand ctx, String globalTransactionId,
            int transactionBlockId) {
        BValue[] args = {new BString(globalTransactionId), new BInteger(transactionBlockId)};
        invokeCoordinatorFunction(ctx, TransactionConstants.COORDINATOR_ABORT_TRANSACTION, args);
    }

    public static boolean isInitiator(Strand ctx, String globalTransactionId,
            int transactionBlockId) {
        BValue[] args = {new BString(globalTransactionId), new BInteger(transactionBlockId)};
        BValue[] returns = invokeCoordinatorFunction(ctx, TransactionConstants.COORDINATOR_IS_INITIATOR, args);
        return ((BBoolean) returns[0]).booleanValue();
    }

    private static void checkTransactionCoordinatorError(BValue value, Strand ctx, String errMsg) {
        if (value.getType().getTag() == TypeTags.ERROR_TAG) {
            throw new BallerinaException(errMsg + ((BError) value).details);
        }
    }

    private static BValue[] invokeCoordinatorFunction(Strand ctx, String functionName, BValue[] args) {
        PackageInfo packageInfo = ctx.programFile.getPackageInfo(TransactionConstants.COORDINATOR_PACKAGE);
        FunctionInfo functionInfo = packageInfo.getFunctionInfo(functionName);
        return BVMExecutor.executeFunction(functionInfo.getPackageInfo().getProgramFile(), functionInfo, args);
    }
}
