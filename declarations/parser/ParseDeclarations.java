package ast.declarations.parser;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.lir.StandardOp.NullCheck;

import ast._typesnew.CEnumType;
import ast._typesnew.CStructType;
import ast._typesnew.CType;
import ast._typesnew.decl.CDecl;
import ast._typesnew.parser.ParseBase;
import ast._typesnew.parser.ParseDecl;
import ast._typesnew.util.TypeMerger;
import ast.declarations.DesignatedInitializer;
import ast.declarations.Designation;
import ast.declarations.Designator;
import ast.declarations.Initializer;
import ast.declarations.InitializerList;
import ast.declarations.InitializerListEntry;
import ast.declarations.main.Declaration;
import ast.expr.main.CExpression;
import ast.expr.parser.ParseExpression;
import ast.expr.sem.ConstexprEval;
import ast.parse.NullChecker;
import ast.parse.Parse;
import ast.symtabg.elements.CSymbol;
import jscan.hashed.Hash_ident;
import jscan.tokenize.T;
import jscan.tokenize.Token;

public class ParseDeclarations {
  private final Parse p;

  public ParseDeclarations(Parse parser) {
    this.p = parser;
  }

  //declaration
  //    : declaration_specifiers ';'
  //    | declaration_specifiers init_declarator_list ';'
  //    ;

  //  init_declarator_list
  //    : init_declarator
  //    | init_declarator_list ',' init_declarator
  //    ;

  //  init_declarator
  //    : declarator '=' initializer
  //    | declarator
  //    ;

  //  initializer
  //    : assignment_expression
  //    | '{' initializer_list '}'
  //    | '{' initializer_list ',' '}'
  //    ;

  //XXX: c89
  //  initializer_list
  //    : initializer
  //    | initializer_list ',' initializer
  //    ;

  //XXX: c99
  //  initializer_list
  //    : designation initializer
  //    | initializer
  //    | initializer_list ',' designation initializer
  //    | initializer_list ',' initializer
  //    ;

  //  designation
  //    : designator_list '='
  //    ;
  //
  //  designator_list
  //      : designator
  //      | designator_list designator
  //      ;
  //  
  //  designator
  //      : '[' constant_expression ']'
  //      | '.' IDENTIFIER
  //      ;

  //  declaration
  //      : declaration_specifiers ';'
  //      | declaration_specifiers init_declarator_list ';'
  //      | static_assert_declaration
  //      ;

  public Declaration parseDeclaration() {

    Token startLocation = p.tok();

    // TODO: more clean.
    if (isStaticAssertAndItsOk()) {
      return new Declaration();
    }

    CType basetype = new ParseBase(p).parseBase();

    /// this may be struct/union/enum declaration
    ///
    if (p.tp() == T.T_SEMI_COLON) {
      Token endLocation = p.semicolon();

      boolean isStructUnionEnum = basetype.isStrUnion() || basetype.isEnumeration();
      if (!isStructUnionEnum) {
        p.perror("expect struct/union/enum declaration. but was: " + basetype.toString());
      }

      // semicolon after mean: this declaration has no name, no declarator after...
      // if this aggregate declared without name in function-scope, it NOT change stack-size.

      final Declaration agregate = new Declaration(startLocation, endLocation, basetype);
      return agregate;
    }

    List<CSymbol> initDeclaratorList = parseInitDeclaratorList(basetype);
    Token endLocation = p.semicolon();

    final Declaration declaration = new Declaration(startLocation, endLocation, initDeclaratorList);
    return declaration;
  }

  //  static_assert_declaration
  //    : STATIC_ASSERT '(' constant_expression ',' STRING_LITERAL ')' ';'
  //    ;

  public boolean isStaticAssertAndItsOk() {
    if (!p.tok().isIdent(Hash_ident._Static_assert_ident)) {
      return false;
    }

    p.checkedMoveIdent(Hash_ident._Static_assert_ident);
    p.lparen();

    CExpression ce = new ParseExpression(p).e_const_expr();
    p.checkedMove(T.T_COMMA);

    Token message = p.checkedGetT(T.TOKEN_STRING);
    p.rparen();
    p.semicolon();

    long sares = new ConstexprEval(p).ce(ce);
    if (sares == 0) {
      p.perror("static-assert fail with message: " + message.getValue());
    }

    return true;
  }

  public List<CSymbol> parseInitDeclaratorList(CType basetype) {
    List<CSymbol> initDeclaratorList = new ArrayList<CSymbol>(0);

    CSymbol initDeclarator = parseInitDeclarator(basetype);
    initDeclaratorList.add(initDeclarator);

    while (p.tp() == T.T_COMMA) {
      p.move();

      CSymbol initDeclaratorSeq = parseInitDeclarator(basetype);
      initDeclaratorList.add(initDeclaratorSeq);
    }

    return initDeclaratorList;
  }

  private CSymbol parseInitDeclarator(CType basetype) {
    //  init_declarator
    //    : declarator '=' initializer
    //    | declarator
    //    ;

    Token saved = p.tok();

    CDecl decl = new ParseDecl(p).parseDecl();
    CType type = TypeMerger.build(basetype, decl);

    if (p.tp() != T.T_ASSIGN) {
      final CSymbol sym = new CSymbol(decl.getName(), type, saved);
      p.defineSym(decl.getName(), sym);
      return sym;
    }

    p.checkedMove(T.T_ASSIGN);
    Initializer initializer = parseInitializer();

    if (decl.isAstract()) {
    }

    final CSymbol sym = new CSymbol(decl.getName(), type, initializer, saved);
    p.defineSym(decl.getName(), sym);

    return sym;
  }

  private Initializer parseInitializer() {

    //  initializer
    //    : assignment_expression
    //    | '{' initializer_list '}'
    //    | '{' initializer_list ',' '}'
    //    ;
    //
    //  initializer_list
    //    : initializer
    //    | initializer_list ',' initializer
    //    ;

    if (p.tp() != T.T_LEFT_BRACE) {
      CExpression assignment = new ParseExpression(p).e_assign();
      return new Initializer(assignment);
    }

    p.checkedMove(T.T_LEFT_BRACE);

    // if is empty array initialization - return initializer with empty initializer-list
    // int a[5] = {};
    if (p.tp() == T.T_RIGHT_BRACE) {
      p.checkedMove(T.T_RIGHT_BRACE);
      return new Initializer(new InitializerList());
    }

    // otherwise - recursively expand braced initializers
    //
    InitializerList initializerList = parseInitializerList(); // XXX: taint comma case here
    p.checkedMove(T.T_RIGHT_BRACE);

    return new Initializer(initializerList);
  }

  public InitializerList parseInitializerList() {

    // c89
    //  initializer_list
    //    : initializer
    //    | initializer_list ',' initializer
    //    ;

    // c99
    //  initializer_list
    //    : designation initializer
    //    | initializer
    //    | initializer_list ',' designation initializer
    //    | initializer_list ',' initializer
    //    ;

    InitializerList initializerList = new InitializerList();

    InitializerListEntry entry = parseInitializerListEntry();
    initializerList.push(entry);

    while (p.tp() == T.T_COMMA) {

      // | '{' initializer_list ',' '}'
      //
      Token lookBrace = p.getTokenlist().peek();
      if (lookBrace.ofType(T.T_RIGHT_BRACE)) {
        p.checkedMove(T.T_COMMA);
        return initializerList;
      }

      p.checkedMove(T.T_COMMA);

      InitializerListEntry initializerSeq = parseInitializerListEntry();
      initializerList.push(initializerSeq);
    }

    return initializerList;
  }

  private InitializerListEntry parseInitializerListEntry() {

    // initializer_list
    //   : designation initializer
    //   | initializer
    //   | initializer_list ',' designation initializer
    //   | initializer_list ',' initializer
    //   ;
    // 
    // designation
    //   : designator_list '='
    //   ;
    // 
    // designator_list
    //   : designator
    //   | designator_list designator
    //   ;
    // 
    // designator
    //   : '[' constant_expression ']'
    //   | '.' IDENTIFIER
    //   ;

    if (p.tp() == T.T_LEFT_BRACKET || p.tp() == T.T_DOT) {

      Designation designation = new Designation();
      parseDesignatorListTo(p, designation);

      p.checkedMove(T.T_ASSIGN);
      Initializer initializer = parseInitializer();

      final DesignatedInitializer designatedInitializer = new DesignatedInitializer(designation, initializer);
      return new InitializerListEntry(designatedInitializer);

    }

    Initializer initializer = parseInitializer();
    return new InitializerListEntry(initializer);
  }

  private void parseDesignatorListTo(Parse p, Designation designation) {

    // designator_list
    //   : designator
    //   | designator_list designator
    //   ;
    // 
    // designator
    //   : '[' constant_expression ']'
    //   | '.' IDENTIFIER
    //   ;

    for (;;) {

      if (p.tp() == T.T_LEFT_BRACKET) {
        p.lbracket();
        CExpression expr = new ParseExpression(p).e_const_expr();
        p.rbracket();
        designation.push(new Designator(expr));
      }

      else if (p.tp() == T.T_DOT) {
        p.move();
        Token ident = p.expectIdentifier();
        designation.push(new Designator(ident));
      }

      else {
        break;
      }
    }

  }

}
