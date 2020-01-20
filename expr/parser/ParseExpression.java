package ast.expr.parser;

import static jscan.tokenize.T.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ast._typesnew.CType;
import ast.declarations.InitializerList;
import ast.declarations.parser.ParseDeclarations;
import ast.expr.main.CExpression;
import ast.expr.main.CExpressionBase;
import ast.expr.sem.CExpressionBuilder;
import ast.parse.Parse;
import ast.parse.ParseState;
import ast.parse.Pcheckers;
import ast.symtabg.elements.CSymbol;
import jscan.cstrtox.C_strtox;
import jscan.hashed.Hash_ident;
import jscan.tokenize.T;
import jscan.tokenize.Token;

class CompoundAssignInfo {

  //@formatter:off
  private static Map<T, T> asopmap = new HashMap<T, T>();
  static {
    asopmap.put(T_TIMES_EQUAL     , T_TIMES);
    asopmap.put(T_PERCENT_EQUAL   , T_PERCENT);
    asopmap.put(T_DIVIDE_EQUAL    , T_DIVIDE);
    asopmap.put(T_PLUS_EQUAL      , T_PLUS);
    asopmap.put(T_MINUS_EQUAL     , T_MINUS);
    asopmap.put(T_LSHIFT_EQUAL    , T_LSHIFT);
    asopmap.put(T_RSHIFT_EQUAL    , T_RSHIFT);
    asopmap.put(T_AND_EQUAL       , T_AND);
    asopmap.put(T_XOR_EQUAL       , T_XOR);
    asopmap.put(T_OR_EQUAL        , T_OR);
  }
  //@formatter:on

  public static Token getAssignToken(Token from) {
    Token ntoken = new Token(from);
    ntoken.setType(T_ASSIGN);
    ntoken.setValue("=");
    return ntoken;
  }

  public static Token getOperatorFromCompAssign(Token from) {
    Token ntoken = new Token(from);
    ntoken.setType(asopmap.get(from.getType()));
    switch (ntoken.getType()) {
    case T_TIMES:
      ntoken.setValue("*");
      break;
    case T_PERCENT:
      ntoken.setValue("%");
      break;
    case T_DIVIDE:
      ntoken.setValue("/");
      break;
    case T_PLUS:
      ntoken.setValue("+");
      break;
    case T_MINUS:
      ntoken.setValue("-");
      break;
    case T_LSHIFT:
      ntoken.setValue("<<");
      break;
    case T_RSHIFT:
      ntoken.setValue(">>");
      break;
    case T_AND:
      ntoken.setValue("&");
      break;
    case T_XOR:
      ntoken.setValue("^");
      break;
    case T_OR:
      ntoken.setValue("|");
      break;
    default:
      break;
    }
    return ntoken;
  }
}

public class ParseExpression {
  private final Parse parser;

  public ParseExpression(Parse parser) {
    this.parser = parser;
  }

  public CExpression e_expression() {
    CExpression e = e_assign();

    while (parser.tp() == T.T_COMMA) {
      Token saved = parser.checkedGetT(T.T_COMMA);
      e = CExpressionBuilder.comma(saved, saved.getType(), e, e_expression());
    }

    return e;
  }

  public CExpression e_const_expr() {
    return e_cnd();
  }

  public CExpression getExprInParen() {
    parser.checkedMove(T_LEFT_PAREN);
    CExpression e = e_expression();
    parser.checkedMove(T.T_RIGHT_PAREN);
    return e;
  }

  private boolean isCompoundAssign(Token what) {
    return Pcheckers.isAssignOperator(what) && !what.ofType(T.T_ASSIGN);
  }

  public CExpression e_assign() {
    CExpression lhs = e_cnd();

    // if simple, then: this...
    //
    //    if (parser.isAssignOperator()) {
    //      Token saved = parser.tok();
    //      parser.move();
    //      final CExpression rhs = e_assign();
    //      lhs = CExpressionBuilder.assign(saved, lhs, rhs);
    //    }

    if (parser.isAssignOperator()) {

      Token saved = parser.tok();

      if (isCompoundAssign(saved)) {
        parser.move();

        // linearize compound assign
        // a+=b :: a=a+b
        //
        // += lhs(a) rhs(b)
        // = lhs(a) rhs( + lhs(a) rhs(b) )

        Token assignOperator = CompoundAssignInfo.getAssignToken(saved);
        Token binaryOperator = CompoundAssignInfo.getOperatorFromCompAssign(saved);

        CExpression rhs = CExpressionBuilder.binary(binaryOperator, parser, lhs, e_assign());
        lhs = CExpressionBuilder.assign(assignOperator, lhs, rhs);
      }

      else {

        parser.move();
        lhs = CExpressionBuilder.assign(saved, lhs, e_assign());
      }

    }

    return lhs;
  }

  private CExpression e_cnd() {
    CExpression res = e_lor();
    if (parser.tp() != T_QUESTION) {
      return res;
    }
    Token saved = parser.tok();
    parser.move();
    CExpression btrue = e_expression();
    parser.checkedMove(T_COLON);
    return new CExpression(res, btrue, e_cnd(), saved);
  }

  private CExpression e_lor() {
    CExpression e = e_land();
    while (parser.tp() == T_OR_OR) {
      Token saved = parser.tok();
      parser.move();
      e = CExpressionBuilder.binary(saved, parser, e, e_land());
    }
    return e;
  }

  private CExpression e_land() {
    CExpression e = e_bor();
    while (parser.tp() == T_AND_AND) {
      Token saved = parser.tok();
      parser.move();
      e = CExpressionBuilder.binary(saved, parser, e, e_bor());
    }
    return e;
  }

  private CExpression e_bor() {
    CExpression e = e_bxor();
    while (parser.tp() == T_OR) {
      Token saved = parser.tok();
      parser.move();
      e = CExpressionBuilder.binary(saved, parser, e, e_bxor());
    }
    return e;
  }

  private CExpression e_bxor() {
    CExpression e = e_band();
    while (parser.tp() == T_XOR) {
      Token saved = parser.tok();
      parser.move();
      e = CExpressionBuilder.binary(saved, parser, e, e_band());
    }
    return e;
  }

  private CExpression e_band() {
    CExpression e = e_equality();
    while (parser.tp() == T_AND) {
      Token saved = parser.tok();
      parser.move();
      e = CExpressionBuilder.binary(saved, parser, e, e_equality());
    }
    return e;
  }

  private CExpression e_equality() {
    CExpression e = e_relational();
    while (parser.tp() == T_EQ || parser.tp() == T_NE) {
      Token saved = parser.tok();
      parser.move();
      e = CExpressionBuilder.binary(saved, parser, e, e_relational());
    }
    return e;
  }

  private CExpression e_relational() {
    CExpression e = e_shift();
    while (parser.tp() == T_LT || parser.tp() == T_GT || parser.tp() == T_LE || parser.tp() == T_GE) {
      Token saved = parser.tok();
      parser.move();
      e = CExpressionBuilder.binary(saved, parser, e, e_shift());
    }
    return e;
  }

  private CExpression e_shift() {
    CExpression e = e_add();
    while (parser.tp() == T_LSHIFT || parser.tp() == T_RSHIFT) {
      Token saved = parser.tok();
      parser.move();
      e = CExpressionBuilder.binary(saved, parser, e, e_add());
    }
    return e;
  }

  private CExpression e_add() {
    CExpression e = e_mul();
    while (parser.tp() == T_PLUS || parser.tp() == T_MINUS) {
      Token saved = parser.tok();
      parser.move();
      e = CExpressionBuilder.binary(saved, parser, e, e_mul());
    }
    return e;
  }

  private CExpression e_mul() {
    CExpression e = e_cast();
    while (parser.tp() == T_TIMES || parser.tp() == T_DIVIDE || parser.tp() == T_PERCENT) {
      Token saved = parser.tok();
      parser.move();
      e = CExpressionBuilder.binary(saved, parser, e, e_cast());
    }
    return e;
  }

  private CExpression e_cast() {

    if (parser.tp() == T_LEFT_PAREN) {
      ParseState state = new ParseState(parser);

      Token peek = parser.getTokenlist().peek();
      if (parser.isDeclSpecStart(peek)) {

        Token lparen = parser.lparen();
        CType typeName = parser.parse_typename();
        parser.rparen();

        // ambiguous
        // "(" type-name ")" "{" initializer-list "}"
        // "(" type-name ")" "{" initializer-list "," "}"

        if (parser.tp() != T.T_LEFT_BRACE) {
          final CExpression tocast = e_cast();
          return CExpressionBuilder.doCast(parser, typeName, tocast, lparen, false);
        }
      }

      parser.restoreState(state);

    }

    return e_unary();
  }

  private CExpression e_unary() {

    if (parser.isUnaryOperator() || parser.tp() == T_TIMES || parser.tp() == T_AND) {
      Token operator = parser.tok();
      parser.move();
      return new CExpression(operator, e_cast());
    }

    if (parser.tp() == T.T_PLUS_PLUS || parser.tp() == T_MINUS_MINUS) {
      Token operator = parser.tok();
      parser.move();
      return new CExpression(CExpressionBase.EPREINCDEC, operator, e_unary());
    }

    if (parser.tok().isIdent(Hash_ident.sizeof_ident)) {
      return eSizeof();
    }

    return e_postfix();
  }

  private CExpression eSizeof() {
    Token id = parser.checkedMoveIdent(Hash_ident.sizeof_ident);

    if (parser.tp() == T_LEFT_PAREN) {
      parser.lparen();

      if (parser.isDeclSpecStart()) {

        CType typename = parser.parse_typename();
        parser.rparen();

        C_strtox strtox = new C_strtox(String.format("%d", typename.getSize()));
        final CExpression ret = CExpressionBuilder.number(strtox, id, parser, false);
        //        TypeApplier.applyType(ret, parser);

        return ret;

      } else {

        CExpression sizeofexpr = e_expression();
        //        TypeApplier.applyType(sizeofexpr, parser);
        if (sizeofexpr.getResultType() == null) {
          parser.perror("no type for " + sizeofexpr.getBase().toString() + ": " + sizeofexpr.toString());
        }

        parser.rparen();

        C_strtox strtox = new C_strtox(String.format("%d", sizeofexpr.getResultType().getSize()));
        return CExpressionBuilder.number(strtox, id, parser, false);

      }

    }

    // sizeof 1

    CExpression sizeofexpr = e_unary();
    //    TypeApplier.applyType(sizeofexpr, parser);

    if (sizeofexpr.getResultType() == null) {
      parser.perror("no type for " + sizeofexpr.getBase().toString() + ": " + sizeofexpr.toString());
    }

    C_strtox strtox = new C_strtox(String.format("%d", sizeofexpr.getResultType().getSize()));
    return CExpressionBuilder.number(strtox, id, parser, false);

  }

  private CExpression e_postfix() {

    // "(" type-name ")" "{" initializer-list "}"
    // "(" type-name ")" "{" initializer-list "," "}"

    if (parser.tp() == T_LEFT_PAREN && parser.isDeclSpecStart(parser.getTokenlist().peek())) {
      ParseState state = new ParseState(parser);

      parser.lparen();
      CType typename = parser.parse_typename();
      parser.rparen();

      // if next is `{` return compound, restore state otherwise
      //
      if (parser.tp() == T.T_LEFT_BRACE) {
        Token saved = parser.tok();

        InitializerList initializerList = new ParseDeclarations(parser).parseInitializerList();
        return new CExpression(typename, initializerList, saved);
      }

      parser.restoreState(state);
    }

    CExpression lhs = e_prim();

    for (;;) {

      // function - call
      //
      if (parser.tp() == T_LEFT_PAREN) {
        Token lparen = parser.lparen();

        List<CExpression> arglist = new ArrayList<CExpression>();

        if (parser.tp() != T_RIGHT_PAREN) {
          CExpression onearg = e_assign();
          arglist.add(onearg);

          while (parser.tp() == T.T_COMMA) {
            parser.move();

            CExpression oneargSeq = e_assign();
            arglist.add(oneargSeq);
          }
        }

        lhs = new CExpression(lhs, arglist, lparen);
        parser.rparen();
      }

      // direct|indirect selection
      //
      else if (parser.tp() == T_DOT || parser.tp() == T_ARROW) {
        Token operator = parser.tok();
        parser.move(); // move . or ->

        Token ident = parser.expectIdentifier();
        lhs = new CExpression(lhs, operator, ident.getIdent());
      }

      // ++ --
      //
      else if (parser.tp() == T.T_PLUS_PLUS || parser.tp() == T_MINUS_MINUS) {
        Token operator = parser.tok();
        parser.move();
        lhs = new CExpression(CExpressionBase.EPOSTINCDEC, operator, lhs);
      }

      // array-subscript
      //
      else if (parser.tp() == T.T_LEFT_BRACKET) {
        while (parser.tp() == T_LEFT_BRACKET) {
          Token lbrack = parser.lbracket();
          lhs = new CExpression(CExpressionBase.ESUBSCRIPT, lhs, e_expression(), lbrack);
          parser.rbracket();
        }
      }

      else {
        break;
      }
    }

    return lhs;
  }

  //  primary_expression
  //      : IDENTIFIER
  //      | constant
  //      | string
  //      | '(' expression ')'
  //      | generic_selection
  //      ;

  private CExpression e_prim() {

    if (parser.tok().isIdent(Hash_ident._Generic_ident)) {
      Token saved = parser.tok();
      return new ExpandGenericResult(parser).getGenericResult(saved);
    }

    if (parser.tp() == TOKEN_NUMBER || parser.tp() == TOKEN_CHAR || parser.tp() == TOKEN_STRING) {
      Token saved = parser.tok();
      parser.move();
      if (saved.ofType(TOKEN_STRING)) {
        CExpression e = new CExpression(saved.getValue(), saved);
        return e;
      } else {

        //TODO:NUMBERS
        String toeval = "";
        if (saved.ofType(TOKEN_CHAR)) {
          toeval = String.format("%d", saved.getCharconstant().getV());
        } else {
          toeval = saved.getValue();
        }

        // TODO:NUMBERS
        C_strtox strtox = new C_strtox(toeval);
        CExpression e = CExpressionBuilder.number(strtox, saved, parser, false);
        return e;
      }
    }

    if (parser.tp() == T.TOKEN_IDENT) {
      Token saved = parser.tok();
      parser.move();

      CSymbol sym = parser.getSym(saved.getIdent());
      if (sym == null && parser.isSemanticEnable()) {
        parser.perror("symbol '" + saved.getValue() + "' was not declared in the scope.");
      }

      CExpression e = CExpressionBuilder.esymbol(sym, saved, false);
      return e;
    }

    if (parser.tp() == T_LEFT_PAREN) {
      parser.move();
      CExpression e = e_expression();
      parser.checkedMove(T_RIGHT_PAREN);
      return e;
    }

    parser.perror("something wrong in expression...");
    return null; // you never return this ;)

  }

}
