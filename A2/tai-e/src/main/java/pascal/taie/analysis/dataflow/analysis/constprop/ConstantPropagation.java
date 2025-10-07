/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.dataflow.analysis.constprop;

import fj.P;
import pascal.taie.analysis.dataflow.analysis.AbstractDataflowAnalysis;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.*;
import pascal.taie.ir.stmt.DefinitionStmt;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.type.PrimitiveType;
import pascal.taie.language.type.Type;
import pascal.taie.util.AnalysisException;

import java.rmi.UnexpectedException;

public class ConstantPropagation extends
        AbstractDataflowAnalysis<Stmt, CPFact> {

    public static final String ID = "constprop";

    public ConstantPropagation(AnalysisConfig config) {
        super(config);
    }

    @Override
    public boolean isForward() {
        return true;
    }

    @Override
    public CPFact newBoundaryFact(CFG<Stmt> cfg) {
        // TODO - finish me
        CPFact cpFact = new CPFact();
        cfg.getIR().getParams().forEach(param -> {
            cpFact.update(param, Value.getNAC());
        });
        return cpFact;
    }

    @Override
    public CPFact newInitialFact(Stmt node) {
        // TODO - finish me
        CPFact cpFact = new CPFact();
        if (node instanceof DefinitionStmt<?,?> defStmt) {
            LValue lValue = defStmt.getLValue();
            if (lValue instanceof Var var) {
                if (canHoldInt(var)) {
                    cpFact.update(var, Value.getNAC());
                }
            }
        }
        return cpFact;
    }

    @Override
    public void meetInto(CPFact fact, CPFact target) {
        // TODO - finish me
        fact.entries().forEach(entry -> {
            Var key = entry.getKey();
            Value value1 = entry.getValue();
            if (target.keySet().contains(key)) {
                Value value2 = target.get(key);
                target.update(key, meetValue(value1, value2));
            } else {
                target.update(key, value1);
            }
        });
    }

    /**
     * Meets two Values.
     */
    public Value meetValue(Value v1, Value v2) {
        // TODO - finish me
        if (v1.isNAC() || v2.isNAC()) {
            return Value.getNAC();
        }
        if (v1.isUndef()) {
            return v2;
        }
        if (v2.isUndef()) {
            return v1;
        }
        if (v1.isConstant() && v2.isConstant()) {
            if (v1.equals(v2)) {
                return v1;
            } else {
                return Value.getNAC();
            }
        }
        throw new RuntimeException("should not reach here");
    }

    @Override
    public boolean transferNode(Stmt stmt, CPFact in, CPFact out) {
        // TODO - finish me
        CPFact outCopy = out.copy();
        CPFact inCopy = in.copy();

        if (stmt instanceof DefinitionStmt<?, ?> defStmt) {
            LValue lValue = defStmt.getLValue();
            if (lValue instanceof Var var) {
                inCopy.remove(var);

                RValue rValue = defStmt.getRValue();
                Value evaluated = evaluate(rValue, in);
                inCopy.update(var, evaluated);
            }
        }
        out.copyFrom(inCopy);
        return !out.equals(outCopy);
    }

    /**
     * @return true if the given variable can hold integer value, otherwise false.
     */
    public static boolean canHoldInt(Var var) {
        Type type = var.getType();
        if (type instanceof PrimitiveType) {
            switch ((PrimitiveType) type) {
                case BYTE:
                case SHORT:
                case INT:
                case CHAR:
                case BOOLEAN:
                    return true;
            }
        }
        return false;
    }

    /**
     * Evaluates the {@link Value} of given expression.
     *
     * @param exp the expression to be evaluated
     * @param in  IN fact of the statement
     * @return the resulting {@link Value}
     */
    public static Value evaluate(Exp exp, CPFact in) {
        // TODO - finish me
        if (exp instanceof Var var) {
            return in.get(var);
        } else if (exp instanceof IntLiteral intLiteral) {
            return Value.makeConstant(intLiteral.getValue());
        } else if (exp instanceof BinaryExp binaryExp) {
            Value v1 = in.get(binaryExp.getOperand1());
            Value v2 = in.get(binaryExp.getOperand2());
            if (v1.isConstant() && v2.isConstant()) {
                if (binaryExp instanceof ArithmeticExp arithmeticExp) {
                    return switch (arithmeticExp.getOperator()) {
                        case ADD -> Value.makeConstant(v1.getConstant() + v2.getConstant());
                        case SUB -> Value.makeConstant(v1.getConstant() - v2.getConstant());
                        case MUL -> Value.makeConstant(v1.getConstant() * v2.getConstant());
                        case DIV -> {
                            if (v2.getConstant() == 0) {
                                yield Value.getUndef();
                            }
                            yield Value.makeConstant(v1.getConstant() / v2.getConstant());
                        }
                        case REM -> {
                            if (v2.getConstant() == 0) {
                                yield Value.getUndef();
                            }
                            yield Value.makeConstant(v1.getConstant() % v2.getConstant());
                        }
                    };
                } else if (binaryExp instanceof ConditionExp conditionExp) {
                    return switch (conditionExp.getOperator()) {
                        case EQ -> v1.getConstant() == v2.getConstant() ?
                                Value.makeConstant(1) : Value.makeConstant(0);
                        case NE -> v1.getConstant() != v2.getConstant() ?
                                Value.makeConstant(1) : Value.makeConstant(0);
                        case LT -> v1.getConstant() < v2.getConstant() ?
                                Value.makeConstant(1) : Value.makeConstant(0);
                        case GT -> v1.getConstant() > v2.getConstant() ?
                                Value.makeConstant(1) : Value.makeConstant(0);
                        case LE -> v1.getConstant() <= v2.getConstant() ?
                                Value.makeConstant(1) : Value.makeConstant(0);
                        case GE -> v1.getConstant() >= v2.getConstant() ?
                                Value.makeConstant(1) : Value.makeConstant(0);
                    };
                } else if (binaryExp instanceof BitwiseExp bitwiseExp) {
                    return switch (bitwiseExp.getOperator()) {
                        case OR -> Value.makeConstant(v1.getConstant() | v2.getConstant());
                        case AND -> Value.makeConstant(v1.getConstant() & v2.getConstant());
                        case XOR -> Value.makeConstant(v1.getConstant() ^ v2.getConstant());
                    };
                } else if (binaryExp instanceof ShiftExp shiftExp) {
                    return switch (shiftExp.getOperator()) {
                        case SHL -> Value.makeConstant(v1.getConstant() << v2.getConstant());
                        case SHR -> Value.makeConstant(v1.getConstant() >> v2.getConstant());
                        case USHR -> Value.makeConstant(v1.getConstant() >>> v2.getConstant());
                    };
                }
            } else if (v1.isNAC() || v2.isNAC()) {
                return Value.getNAC();
            }
            return Value.getUndef();
        }
        return Value.getUndef();
    }
}
