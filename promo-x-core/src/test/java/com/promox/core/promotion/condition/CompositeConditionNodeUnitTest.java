package com.promox.core.promotion.condition;

import com.promox.core.promotion.internal.service.condition.CompositeConditionNode;
import com.promox.core.promotion.internal.service.condition.ConditionNode;
import com.promox.core.promotion.internal.context.EngineWorkspace;

import static com.promox.core.promotion.internal.service.condition.CompositeConditionNode.LogicalOperator;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class CompositeConditionNodeUnitTest {
    @Mock
    private EngineWorkspace workspace;

    @Mock
    private ConditionNode childA;

    @Mock
    private ConditionNode childB;

    @Mock
    private ConditionNode childC;


    @Nested
    @DisplayName("AND Operator Unit Logic")
    class AndOperatorUnitTests {

        @Test
        @DisplayName("Returns true when all children evaluate to true")
        void evaluate_AndOperator_AllTrue_ReturnsTrue() {
            when(childA.evaluate(workspace)).thenReturn(true);
            when(childB.evaluate(workspace)).thenReturn(true);

            var node = new CompositeConditionNode(LogicalOperator.AND, List.of(childA, childB));

            assertThat(node.evaluate(workspace)).isTrue();
            verify(childA).evaluate(workspace);
            verify(childB).evaluate(workspace);
        }

        @Test
        @DisplayName("Returns false and short-circuits on first false evaluation")
        void evaluate_AndOperator_ShortCircuitsOnFirstFalse() {
            when(childA.evaluate(workspace)).thenReturn(false);

            var node = new CompositeConditionNode(LogicalOperator.AND, List.of(childA, childB, childC));

            assertThat(node.evaluate(workspace)).isFalse();

            // Verification: childB and childC must never be evaluated
            verify(childA, times(1)).evaluate(workspace);
            verify(childB, never()).evaluate(workspace);
            verify(childC, never()).evaluate(workspace);
        }
    }

    @Nested
    @DisplayName("OR Operator Unit Logic")
    class OrOperatorUnitTests {

        @Test
        @DisplayName("Returns false when all children evaluate to false")
        void evaluate_OrOperator_AllFalse_ReturnsFalse() {
            when(childA.evaluate(workspace)).thenReturn(false);
            when(childB.evaluate(workspace)).thenReturn(false);

            var node = new CompositeConditionNode(LogicalOperator.OR, List.of(childA, childB));

            assertThat(node.evaluate(workspace)).isFalse();
            verify(childA).evaluate(workspace);
            verify(childB).evaluate(workspace);
        }

        @Test
        @DisplayName("Returns true and short-circuits on first true evaluation")
        void evaluate_OrOperator_ShortCircuitsOnFirstTrue() {
            when(childA.evaluate(workspace)).thenReturn(true);

            var node = new CompositeConditionNode(LogicalOperator.OR, List.of(childA, childB, childC));

            assertThat(node.evaluate(workspace)).isTrue();

            // Verification: childB and childC must never be evaluated
            verify(childA, times(1)).evaluate(workspace);
            verify(childB, never()).evaluate(workspace);
            verify(childC, never()).evaluate(workspace);
        }
    }

    @Nested
    @DisplayName("Boundary & Metadata Contract")
    class MetadataAndBoundaryTests {

        @Test
        @DisplayName("Returns true when children list is empty")
        void evaluate_EmptyChildren_ReturnsTrue() {
            var node = new CompositeConditionNode(LogicalOperator.AND, List.of());

            assertThat(node.evaluate(workspace)).isTrue();
        }
    }
}
