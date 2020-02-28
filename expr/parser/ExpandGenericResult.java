package ast.expr.parser;

import static jscan.tokenize.T.T_COLON;

import java.util.ArrayList;
import java.util.List;

import jscan.hashed.Hash_ident;
import jscan.tokenize.T;
import jscan.tokenize.Token;
import ast._typesnew.CType;
import ast.expr.main.CExpression;
import ast.expr.sem.TypeApplierStage;
import ast.expr.sem.TypeApplier;
import ast.parse.Parse;

class GenericAssociation {
  private final CType typename;
  private final CExpression assignment;

  public GenericAssociation(CType typename, CExpression assignment) {
    this.typename = typename;
    this.assignment = assignment;
  }

  public CType getTypename() {
    return typename;
  }

  public CExpression getAssignment() {
    return assignment;
  }

}

class GenericSelection {
  // generic_selection
  //  : GENERIC '(' assignment_expression ',' generic_assoc_list ')'
  //  ;
  //
  //generic_assoc_list
  //  : generic_association
  //  | generic_assoc_list ',' generic_association
  //  ;
  //
  //generic_association
  //  : type_name ':' assignment_expression
  //  | DEFAULT ':' assignment_expression
  //  ;

  private final CExpression controlExpression;
  private final List<GenericAssociation> associations;
  private CExpression defaultAssociation;

  public GenericSelection(CExpression controlExpression) {
    this.controlExpression = controlExpression;
    this.associations = new ArrayList<GenericAssociation>(0);
  }

  public void push(GenericAssociation e) {
    associations.add(e);
  }

  public CExpression getControlExpression() {
    return controlExpression;
  }

  public List<GenericAssociation> getAssociations() {
    return associations;
  }

  public CExpression getDefaultAssociation() {
    return defaultAssociation;
  }

  public void setDefaultAssociation(CExpression defaultAssociation) {
    this.defaultAssociation = defaultAssociation;
  }

}

public class ExpandGenericResult {
  private final Parse parser;

  public ExpandGenericResult(Parse parser) {
    this.parser = parser;
  }

  public CExpression getGenericResult(Token from) {
    return parseGenericSelection(from);
  }

  //generic_selection
  //  : GENERIC '(' assignment_expression ',' generic_assoc_list ')'
  //  ;
  //
  //generic_assoc_list
  //  : generic_association
  //  | generic_assoc_list ',' generic_association
  //  ;
  //
  //generic_association
  //  : type_name ':' assignment_expression
  //  | DEFAULT ':' assignment_expression
  //  ;

  private CExpression parseGenericSelection(Token from) {
    Token id = parser.checkedMove(Hash_ident._Generic_ident);
    parser.lparen();

    CExpression assignment = e_assign();
    parser.checkedMove(T.T_COMMA);

    GenericSelection genericSelection = new GenericSelection(assignment);
    parseGenericAssociation(genericSelection);

    while (parser.tp() == T.T_COMMA) {
      parser.move();
      parseGenericAssociation(genericSelection);
    }

    parser.rparen();

    CExpression result = selectResultExpression(genericSelection);

    CExpression e = new CExpression(result, id);
    e.setResultType(result.getResultType());

    return e;
  }

  private void parseGenericAssociation(GenericSelection gs) {
    if (parser.tok().isIdent(Hash_ident.default_ident)) {

      if (gs.getDefaultAssociation() != null) {
        parser.perror("duplicate default in generic selection.");
      }

      parser.checkedMove(Hash_ident.default_ident);
      parser.checkedMove(T_COLON);
      gs.setDefaultAssociation(e_assign());
      return;
    }
    final CType typename = parser.parse_typename();
    parser.checkedMove(T_COLON);
    gs.push(new GenericAssociation(typename, e_assign()));
  }

  private CExpression e_assign() {
    return new ParseExpression(parser).e_assign();
  }

  private CExpression selectResultExpression(GenericSelection genericSelection) {
    for (GenericAssociation e : genericSelection.getAssociations()) {
      TypeApplier.applytype(e.getAssignment(), TypeApplierStage.stage_start);
    }

    TypeApplier.applytype(genericSelection.getControlExpression(), TypeApplierStage.generic_control_expr);
    if (genericSelection.getDefaultAssociation() != null) {
      TypeApplier.applytype(genericSelection.getDefaultAssociation(), TypeApplierStage.stage_start);
    }

    CType need = genericSelection.getControlExpression().getResultType();
    if (need == null) {
      parser.perror("no type for control expression.");
    }

    for (GenericAssociation assoc : genericSelection.getAssociations()) {
      if (assoc.getTypename().isEqualTo(need)) {
        return assoc.getAssignment();
      }
    }

    final CExpression defaultAssociation = genericSelection.getDefaultAssociation();
    if (defaultAssociation == null) {
      parser.perror("you need specify default association for this type: " + need.toString());
    }

    return defaultAssociation;
  }

}
