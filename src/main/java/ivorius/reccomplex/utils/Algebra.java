/*
 *  Copyright (c) 2014, Lukas Tenbrink.
 *  * http://lukas.axxim.net
 */

package ivorius.reccomplex.utils;

import com.google.common.base.Function;
import gnu.trove.stack.TIntStack;
import gnu.trove.stack.array.TIntArrayStack;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

/**
 * A list of operators that is able to parse strings to an expression.
 *
 * @param <T> The Algebra's data type.
 */
public class Algebra<T>
{
    @Nonnull
    protected Rules rules;

    protected final List<Operator<T>> operators = new ArrayList<>();

    @Nullable
    protected Logger logger;

    @SafeVarargs
    public Algebra(Operator<T>... operators)
    {
        this(new SimpleRules(), operators);
    }

    @SafeVarargs
    public Algebra(@Nonnull Rules rules, Operator<T>... operators)
    {
        this.rules = rules;
        Collections.addAll(this.operators, operators);
    }

    private static boolean hasAt(String string, String symbol, int index)
    {
        return string.regionMatches(index, symbol, 0, symbol.length());
    }

    @Nonnull
    public Rules getRules()
    {
        return rules;
    }

    public void setRules(@Nonnull Rules rules)
    {
        this.rules = rules;
    }

    @Nullable
    public Logger getLogger()
    {
        return logger;
    }

    public void setLogger(@Nullable Logger logger)
    {
        this.logger = logger;
    }

    @Nullable
    public Expression<T> tryParse(String string)
    {
        try
        {
            return parse(string);
        }
        catch (ParseException e)
        {
            return null;
        }
    }

    @Nonnull
    public Expression<T> parse(String string) throws ParseException
    {
        try
        {
            List<Token> tokens = tokenize(string);
            implode(tokens, 0, 0, tokens.size());

            return ((Token.ExpressionToken<T>) tokens.get(0)).expression;
        }
        catch (ParseException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            if (logger != null)
                logger.error("Internal error when parsing", e);

            throw new ParseException(String.format("%s", e.toString()), 0);
        }
    }

    protected void implode(List<Token> tokens, int minOperatorIndex, int start, int end) throws ParseException
    {
        if (end - start < 1)
        {
            if (tokens.size() > start)
                throw new ParseException("Expected Expression", tokens.get(start).stringIndex);
            else if (tokens.size() > 0)
                throw new ParseException("Expected Expression", tokens.get(tokens.size() - 1).stringIndex);
            else
                throw new ParseException("Expected Expression", 0);
        }

        Token startToken;
        if (end - start == 1 && (startToken = tokens.get(start)) instanceof Token.ConstantToken)
        {
            tokens.remove(start);
            tokens.add(start, new Token.ExpressionToken(startToken.stringIndex, new Constant(((Token.ConstantToken) startToken).identifier)));
            return;
        }

        for (int operatorIndex = minOperatorIndex; operatorIndex < operators.size(); operatorIndex++)
        {
            Operator<T> operator = operators.get(operatorIndex);
            String[] symbols = operator.getSymbols();
            int lastSymbolIndex = symbols.length - 1;
            int numberOfArguments = operator.getNumberOfArguments();

            Stack<BuildingExpression> expressionStack = new Stack<>();
            expressionStack.push(new BuildingExpression(tokens.get(start).stringIndex, start, -1));

            for (int t = start; t < end; t++)
            {
                Token token = tokens.get(t);
                if (token instanceof Token.OperatorToken)
                {
                    Token.OperatorToken operatorToken = (Token.OperatorToken) token;
                    if (operatorToken.operatorIndex < operatorIndex)
                        throw new ParseException("Internal Error (Operator Sorting)", operatorIndex);
                    else if (operatorToken.operatorIndex == operatorIndex)
                    {
                        if (expressionStack.peek().currentSymbolIndex == lastSymbolIndex && operator.hasRightArgument() && operator.hasLeftArgument())
                        {
                            // Evaluate from left to right, so short-circuit asap
                            Integer lastTokenIndex = expressionStack.peek().currentTokenIndex;
                            implode(tokens, minOperatorIndex + 1, lastTokenIndex, t);

                            int difference = (t - lastTokenIndex) - 1;
                            end -= difference; // Account for imploded tokens
                            t -= difference; // Account for imploded tokens

                            finishImploding(tokens, expressionStack.peek().startStringIndex, t - numberOfArguments, t, operator);
                            t -= numberOfArguments - 1; // Account for imploded tokens
                            end -= numberOfArguments - 1; // Account for imploded tokens
                            expressionStack.pop();

                            t--; // Do the same symbol again
                        }
                        else
                        {
                            if (operatorToken.symbolIndex == 0 || operatorToken.symbolIndex == expressionStack.peek().currentSymbolIndex + 1)
                            {
                                if (operatorToken.symbolIndex > 0 || operator.hasLeftArgument())
                                {
                                    Integer lastTokenIndex = expressionStack.peek().currentTokenIndex;
                                    implode(tokens, minOperatorIndex + 1, lastTokenIndex, t);

                                    int difference = (t - lastTokenIndex) - 1;
                                    end -= difference; // Account for imploded tokens
                                    t -= difference; // Account for imploded tokens
                                }

                                if (operatorToken.symbolIndex == 0)
                                    expressionStack.push(new BuildingExpression(operatorToken.stringIndex, t));
                                else
                                {
                                    BuildingExpression currentExpression = expressionStack.peek();
                                    currentExpression.currentStringIndex = operatorToken.stringIndex;
                                    currentExpression.currentTokenIndex = t;
                                    currentExpression.currentSymbolIndex = operatorToken.symbolIndex;
                                }

                                if (expressionStack.peek().currentSymbolIndex == lastSymbolIndex && !operator.hasRightArgument())
                                {
                                    finishImploding(tokens, expressionStack.peek().startStringIndex, t - numberOfArguments, t, operator);

                                    int difference = numberOfArguments - 1;
                                    t -= difference; // Account for imploded tokens
                                    end -= difference; // Account for imploded tokens

                                    expressionStack.pop();
                                }

                                tokens.remove(t--); // Remove symbol
                                end--; // Account for removed symbol
                            }
                            else
                                throw new ParseException(String.format("Unexpected Token '%s'", symbols[operatorToken.symbolIndex]), operatorToken.stringIndex);
                        }
                    }
                }
            }

            while (expressionStack.peek().currentSymbolIndex == lastSymbolIndex && operator.hasRightArgument())
            {
                Integer lastTokenIndex = expressionStack.peek().currentTokenIndex;
                implode(tokens, minOperatorIndex + 1, lastTokenIndex, end);

                int difference = (end - lastTokenIndex) - 1;
                end -= difference; // Account for imploded tokens

                finishImploding(tokens, expressionStack.peek().startStringIndex, end - numberOfArguments, end, operator);
                end -= numberOfArguments - 1;
                expressionStack.pop();
            }

            if (expressionStack.size() > 1)
            {
                String expectedSymbol = symbols[expressionStack.peek().currentSymbolIndex + 1];
                String previousSymbol = symbols[expressionStack.peek().currentSymbolIndex];

                throw new ParseException(String.format("Expected Token '%s'", expectedSymbol), expressionStack.peek().currentStringIndex + previousSymbol.length());
            }
        }

        if (end - start > 1 || !(tokens.get(start) instanceof Token.ExpressionToken))
            throw new ParseException("Expected Operator", tokens.get(start + 1).stringIndex);
    }

    protected void finishImploding(List<Token> tokens, int stringIndex, int start, int end, Operator<T> operator) throws ParseException
    {
        Expression<T>[] expressions = new Expression[end - start];

        if (end - start < 1)
            throw new ParseException("Internal Error (Missing Arguments)", stringIndex);

        for (int i = 0; i < end - start; i++)
        {
            Token removed = tokens.remove(start);
            if (removed instanceof Token.ExpressionToken)
                expressions[i] = ((Token.ExpressionToken<T>) removed).expression;
            else
                throw new ParseException("Internal Error (Unevaluated Token)", stringIndex);
        }

        tokens.add(start, new Token.ExpressionToken(stringIndex, new Operation<>(operator, expressions)));
    }

    protected List<Token> tokenize(String string) throws ParseException
    {
        Character escapeChar = rules.escapeChar();

        int index = 0;
        int variableStart = -1;
        boolean escape = false;
        TIntStack escapes = new TIntArrayStack();
        ArrayList<Token> tokens = new ArrayList<>();

        while (index < string.length())
        {
            char character = string.charAt(index);

            if (rules.isIllegal(character))
                throw new ParseException(String.format("Illegal character '%c'", character), index);
            else if (!escape && escapeChar != null && escapeChar == character)
            {
                escape = true;
                escapes.push(index);
            }
            else if (!escape && rules.isWhitespace(character))
            {
                if (variableStart >= 0)
                    tokens.add(buildConstantToken(string, variableStart, index, escapes));
                variableStart = -1;
            }
            else
            {
                Token.OperatorToken token;

                if (!escape && (token = operatorTokenAt(string, index)) != null)
                {
                    if (variableStart >= 0)
                        tokens.add(buildConstantToken(string, variableStart, index, escapes));
                    variableStart = -1;

                    tokens.add(token);
                    String symbol = operators.get(token.operatorIndex).getSymbols()[token.symbolIndex];
                    index += symbol.length() - 1;
                }
                else if (variableStart < 0)
                    variableStart = index;

                escape = false;
            }

            index++;
        }

        if (variableStart >= 0)
            tokens.add(buildConstantToken(string, variableStart, index, escapes));

        tokens.trimToSize();
        return tokens;
    }

    protected Token.ConstantToken buildConstantToken(String string, int start, int end, TIntStack escapes)
    {
        StringBuilder constant = new StringBuilder(string.substring(start, end));

        while (escapes.size() > 0) // Iterate from the right, so indexes stay the same
            constant.deleteCharAt(escapes.pop() - start);
        escapes.clear();

        return new Token.ConstantToken(start, constant.toString());
    }

    protected Token.OperatorToken operatorTokenAt(String string, int index)
    {
        for (int o = 0; o < operators.size(); o++)
        {
            Operator<T> operator = operators.get(o);
            String[] symbols = operator.getSymbols();

            for (int s = 0; s < symbols.length; s++)
            {
                String symbol = symbols[s];
                if (hasAt(string, symbol, index))
                    return new Token.OperatorToken(index, o, s);
            }
        }

        return null;
    }

    public String escapeWhereNecessary(String string)
    {
        Character escapeChar = rules.escapeChar();

        if (escapeChar != null)
        {
            TIntStack escapes = new TIntArrayStack();
            for (int idx = 0; idx < string.length(); idx++)
            {
                if (rules.isWhitespace(string.charAt(idx)) || operatorTokenAt(string, idx) != null)
                    escapes.push(idx);
            }

            if (escapes.size() > 0)
            {
                StringBuilder builder = new StringBuilder(string);

                while (escapes.size() > 0)
                    builder.insert(escapes.pop(), escapeChar);

                return builder.toString();
            }
        }

        return string;
    }

    protected static class BuildingExpression
    {
        public int startStringIndex;
        public int currentStringIndex;
        public int currentTokenIndex;
        public int currentSymbolIndex;

        public BuildingExpression(int startStringIndex, int currentTokenIndex)
        {
            this.startStringIndex = startStringIndex;
            this.currentStringIndex = startStringIndex;
            this.currentTokenIndex = currentTokenIndex;
            this.currentSymbolIndex = 0;
        }
        public BuildingExpression(int startStringIndex, int currentTokenIndex, int currentSymbolIndex)
        {
            this.startStringIndex = startStringIndex;
            this.currentStringIndex = startStringIndex;
            this.currentTokenIndex = currentTokenIndex;
            this.currentSymbolIndex = currentSymbolIndex;
        }
    }

    protected static abstract class Token
    {
        public int stringIndex;

        public Token(int stringIndex)
        {
            this.stringIndex = stringIndex;
        }

        protected static class ExpressionToken<T> extends Token
        {
            public Expression<T> expression;

            public ExpressionToken(int stringIndex, Expression expression)
            {
                super(stringIndex);
                this.expression = expression;
            }
        }

        protected static class ConstantToken extends Token
        {
            public String identifier;

            public ConstantToken(int stringIndex, String identifier)
            {
                super(stringIndex);
                this.identifier = identifier;
            }
        }

        protected static class OperatorToken extends Token
        {
            public int operatorIndex;
            public int symbolIndex;

            public OperatorToken(int stringIndex, int operatorIndex, int symbolIndex)
            {
                super(stringIndex);
                this.operatorIndex = operatorIndex;
                this.symbolIndex = symbolIndex;
            }
        }
    }

    public abstract static class Expression<T>
    {
        public abstract T evaluate(@Nullable Function<String, T> input);

        public abstract boolean walkVariables(Visitor<String> visitor);

        public abstract String toString(Function<String, String> stringMapper);
    }

    public static class Constant<T> extends Expression<T>
    {
        public String identifier;

        public Constant(String identifier)
        {
            this.identifier = identifier;
        }

        @Override
        public T evaluate(@Nullable Function<String, T> input)
        {
            if (input == null)
                throw new NullPointerException();

            return input.apply(identifier);
        }

        @Override
        public boolean walkVariables(Visitor<String> visitor)
        {
            return visitor.visit(identifier);
        }

        @Override
        public String toString(Function<String, String> stringMapper)
        {
            return stringMapper.apply(identifier);
        }
    }

    public static class Operation<T> extends Expression<T>
    {
        protected Operator<T> operator;
        protected Expression<T>[] expressions;

        @SafeVarargs
        public Operation(Operator<T> operator, Expression<T>... expressions)
        {
            this.operator = operator;
            this.expressions = expressions;
        }

        public Expression getExpression(int index)
        {
            return expressions[index];
        }

        public void setExpression(int index, Expression<T> expression)
        {
            this.expressions[index] = expression;
        }

        @Override
        public T evaluate(@Nullable Function<String, T> input)
        {
            return operator.evaluate(input, expressions);
        }

        @Override
        public boolean walkVariables(Visitor<String> visitor)
        {
            for (Expression expression : expressions)
                if (!expression.walkVariables(visitor))
                    return false;

            return true;
        }

        @Override
        public String toString(Function<String, String> stringMapper)
        {
            StringBuilder builder = new StringBuilder();

            int idx = 0;
            String[] symbols = operator.getSymbols();

            boolean hasSpaceRightOfFirst = operator.hasLeftArgument();
            boolean hasSpaceLeftOfLast = operator.hasRightArgument();

            if (operator.hasLeftArgument())
                builder.append(expressions[idx++].toString(stringMapper));

            for (int i = 0; i < symbols.length; i++)
            {
                if ((i > 0 || operator.hasLeftArgument()) && (i != symbols.length - 1 || hasSpaceLeftOfLast))
                    builder.append(' ');

                builder.append(symbols[i]);

                if ((i < symbols.length - 1 || operator.hasRightArgument()) && (i != 0 || hasSpaceRightOfFirst))
                    builder.append(' ');

                if (i < symbols.length - 1 || operator.hasRightArgument())
                    builder.append(expressions[idx++].toString(stringMapper));
            }

            return builder.toString();
        }
    }

    public abstract static class Operator<T>
    {
        protected boolean hasLeftArgument;
        protected boolean hasRightArgument;

        protected String[] symbols;

        public Operator(boolean hasLeftArgument, boolean hasRightArgument, String... symbols)
        {
            this.hasLeftArgument = hasLeftArgument;
            this.hasRightArgument = hasRightArgument;
            this.symbols = symbols;
        }

        public boolean hasLeftArgument()
        {
            return hasLeftArgument;
        }

        public void setHasLeftArgument(boolean hasLeftArgument)
        {
            this.hasLeftArgument = hasLeftArgument;
        }

        public boolean hasRightArgument()
        {
            return hasRightArgument;
        }

        public void setHasRightArgument(boolean hasRightArgument)
        {
            this.hasRightArgument = hasRightArgument;
        }

        public String[] getSymbols()
        {
            return symbols;
        }

        public void setSymbols(String[] symbols)
        {
            this.symbols = symbols;
        }

        public int getNumberOfArguments()
        {
            return symbols.length - 1 + (hasLeftArgument() ? 1 : 0) + (hasRightArgument() ? 1 : 0);
        }

        public abstract T evaluate(Function<String, T> variableEvaluator, Expression<T>[] expressions);
    }

    public static interface Rules
    {
        Character escapeChar();

        boolean isWhitespace(char character);

        boolean isIllegal(char character);
    }

    public static class SimpleRules implements Rules
    {
        @Nullable
        private Character escapeChar;
        @Nullable
        private char[] whitespace;
        @Nullable
        private char[] illegal;

        public SimpleRules()
        {
            this('\\', null, null);
        }

        public SimpleRules(Character escapeChar, char[] whitespace, char[] illegal)
        {
            this.escapeChar = escapeChar;
            this.whitespace = whitespace;
            this.illegal = illegal;
        }

        @Nullable
        public Character getEscapeChar()
        {
            return escapeChar;
        }

        @Nullable
        public char[] getWhitespace()
        {
            return whitespace;
        }

        public void setWhitespace(@Nullable char[] whitespace)
        {
            this.whitespace = whitespace;
        }

        @Nullable
        public char[] getIllegal()
        {
            return illegal;
        }

        public void setIllegal(@Nullable char[] illegal)
        {
            this.illegal = illegal;
        }

        @Override
        public boolean isWhitespace(char character)
        {
            return whitespace != null
                    ? ArrayUtils.contains(whitespace, character)
                    : Character.isWhitespace(character);
        }

        @Override
        public Character escapeChar()
        {
            return escapeChar;
        }

        @Override
        public boolean isIllegal(char character)
        {
            return illegal != null && ArrayUtils.contains(illegal, character);
        }
    }
}
