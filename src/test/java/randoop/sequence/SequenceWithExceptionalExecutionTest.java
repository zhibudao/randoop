package randoop.sequence;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

import randoop.DummyVisitor;
import randoop.main.GenTests;
import randoop.operation.NonreceiverTerm;
import randoop.operation.TypedClassOperation;
import randoop.operation.TypedOperation;
import randoop.reflection.PublicVisibilityPredicate;
import randoop.test.ContractSet;
import randoop.test.TestCheckGenerator;
import randoop.types.ArrayType;
import randoop.types.GenericClassType;
import randoop.types.JDKTypes;
import randoop.types.JavaTypes;
import randoop.types.ReferenceType;
import randoop.types.Substitution;
import randoop.types.Type;
import randoop.util.MultiMap;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/*
 * This test is to check behavior of sequence predicates on sequence that has an
 * ArrayStoreException due to attempting to assign an array element of the wrong type to an array.
 * A minimal example would be
 *   Collection<String>[] a = (Collection<String>[])new ArrayList[4];
 *   a[0] = new LinkedHashSet<>();
 */
public class SequenceWithExceptionalExecutionTest {

  @Test
  public void testArrayStoreException() {
    ArrayType arrayType =
        ArrayType.ofElementType(JDKTypes.COLLECTION_TYPE.instantiate(JavaTypes.STRING_TYPE));
    ArrayType rawArrayType = ArrayType.ofElementType(JDKTypes.ARRAY_LIST_TYPE.getRawtype());
    Sequence sequence = new Sequence();
    TypedOperation lengthTerm =
        TypedOperation.createNonreceiverInitialization(new NonreceiverTerm(JavaTypes.INT_TYPE, 4));
    sequence = sequence.extend(lengthTerm, new ArrayList<Variable>());
    List<Variable> input = new ArrayList<>();
    input.add(sequence.getLastVariable());
    sequence = sequence.extend(TypedOperation.createArrayCreation(rawArrayType), input);
    input = new ArrayList<>();
    input.add(sequence.getLastVariable());
    sequence = sequence.extend(TypedOperation.createCast(rawArrayType, arrayType), input);
    int arrayValueIndex = sequence.getLastVariable().index;

    Constructor<?> constructor = null;
    try {
      constructor = (LinkedHashSet.class).getConstructor();
    } catch (NoSuchMethodException e) {
      fail("couldn't get default constructor for LinkedHashSet: " + e.getMessage());
    }
    assert constructor != null;
    TypedClassOperation constructorOp = TypedOperation.forConstructor(constructor);
    Substitution<ReferenceType> substitution =
        ((GenericClassType) constructorOp.getDeclaringType())
            .instantiate(JavaTypes.STRING_TYPE)
            .getTypeSubstitution();
    input = new ArrayList<>();
    sequence = sequence.extend(constructorOp.apply(substitution), input);
    int linkedHashSetIndex = sequence.getLastVariable().index;

    sequence = sequence.extend(TypedOperation.createPrimitiveInitialization(JavaTypes.INT_TYPE, 0));
    input = new ArrayList<>();
    input.add(sequence.getVariable(arrayValueIndex));
    input.add(sequence.getLastVariable());
    input.add(sequence.getVariable(linkedHashSetIndex));
    sequence = sequence.extend(TypedOperation.createArrayElementAssignment(arrayType), input);
    System.out.println(sequence);

    ExecutableSequence es = new ExecutableSequence(sequence);
    TestCheckGenerator gen =
        (new GenTests())
            .createTestCheckGenerator(
                new PublicVisibilityPredicate(),
                new ContractSet(),
                new MultiMap<Type, TypedOperation>(),
                new HashSet<TypedOperation>());
    es.execute(new DummyVisitor(), gen);

    assertFalse("sequence should not have unexecuted statements", es.hasNonExecutedStatements());
    assertFalse("sequence should not have failure", es.hasFailure());
    assertFalse("sequence should not have invalid behavior", es.hasInvalidBehavior());
    assertFalse("sequence should not have normal execution", es.isNormalExecution());

    assertThat(
        "exception in last statement",
        es.getNonNormalExecutionIndex(),
        is(equalTo(sequence.size() - 1)));
  }
}
