package ast._typesnew.main;

//@formatter:off
public enum TypeKind {
  TP_POINTER_TO
 ,TP_ARRAY_OF
 ,TP_FUNCTION
 ,TP_STRUCT
 ,TP_ENUM
 ,TP_UNION
 ,TP_BITFIELD
 //
 ,TP_INCOMPLETE
 //
 // builtin's
 ,TP_VOID
 ,TP_BOOL
 ,TP_CHAR
 ,TP_UCHAR
 ,TP_SHORT
 ,TP_USHORT
 ,TP_INT
 ,TP_UINT
 ,TP_LONG
 ,TP_ULONG
 ,TP_LONG_LONG
 ,TP_ULONG_LONG
 ,TP_FLOAT
 ,TP_DOUBLE
 ,TP_LONG_DOUBLE
 ,TP_FLOAT_IMAGINARY
 ,TP_DOUBLE_IMAGINARY
 ,TP_LONG_DOUBLE_IMAGINARY
 ,TP_FLOAT_COMPLEX
 ,TP_DOUBLE_COMPLEX
 ,TP_LONG_DOUBLE_COMPLEX
}