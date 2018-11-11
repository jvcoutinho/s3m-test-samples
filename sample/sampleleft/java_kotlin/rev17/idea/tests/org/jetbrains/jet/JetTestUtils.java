package org.jetbrains.jet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.ErrorHandler;
import org.jetbrains.jet.lang.JetDiagnostic;
import org.jetbrains.jet.lang.psi.JetReferenceExpression;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.BindingTrace;
import org.jetbrains.jet.util.ReadOnlySlice;
import org.jetbrains.jet.util.WritableSlice;

import java.util.Collection;

/**
 * @author abreslav
 */
public class JetTestUtils {
    public static final BindingTrace DUMMY_TRACE = new BindingTrace() {

        @NotNull
        @Override
        public ErrorHandler getErrorHandler() {
            return new ErrorHandler() {
                @Override
                public void unresolvedReference(@NotNull JetReferenceExpression referenceExpression) {
                    throw new IllegalStateException("Unresolved: " + referenceExpression.getText());
                }
            };
        }


        @Override
        public BindingContext getBindingContext() {
            return new BindingContext() {

                @Override
                public Collection<JetDiagnostic> getDiagnostics() {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public <K, V> V get(ReadOnlySlice<K, V> slice, K key) {
                    return DUMMY_TRACE.get(slice, key);
                }
            };
        }

        @Override
        public <K, V> void record(WritableSlice<K, V> slice, K key, V value) {
        }

        @Override
        public <K> void record(WritableSlice<K, Boolean> slice, K key) {
        }

        @Override
        public <K, V> V get(ReadOnlySlice<K, V> slice, K key) {
            if (slice == BindingContext.PROCESSED) return (V) Boolean.FALSE;
            return null;
        }
    };
}
