/* Generated By:JJTree: Do not edit this line. CLVFLoadClass.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=true,VISITOR=true,TRACK_TOKENS=false,NODE_PREFIX=CLVF,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package org.jetel.ctl.ASTnode;

import org.jetel.ctl.TransformLangParser;
import org.jetel.ctl.TransformLangParserVisitor;

public
class CLVFLoadClass extends SimpleNode {
	
	private String className;
	
  /**
	 * @return the className
	 */
	public String getClassName() {
		return className;
	}

	/**
	 * @param className the className to set
	 */
	public void setClassName(String className) {
		this.className = className;
	}

public CLVFLoadClass(int id) {
    super(id);
  }

  public CLVFLoadClass(TransformLangParser p, int id) {
    super(p, id);
  }


  /** Accept the visitor. **/
  @Override
public Object jjtAccept(TransformLangParserVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }

@Override
public SimpleNode duplicate() {
	// TODO Auto-generated method stub
	return null;
}

}
/* JavaCC - OriginalChecksum=e6ac4581b0cbf5fcdd63d4935c3ba9c7 (do not edit this line) */