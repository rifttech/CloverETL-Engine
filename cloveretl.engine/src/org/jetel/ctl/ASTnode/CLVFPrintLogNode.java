/* Generated By:JJTree: Do not edit this line. CLVFPrintLogNode.java */

package org.jetel.ctl.ASTnode;

import org.jetel.ctl.TransformLangParser;
import org.jetel.ctl.TransformLangParserVisitor;

public class CLVFPrintLogNode extends SimpleNode {

	public CLVFPrintLogNode(int id) {
		super(id);
	}

	public CLVFPrintLogNode(TransformLangParser p, int id) {
		super(p, id);
	}

	public CLVFPrintLogNode(CLVFPrintLogNode node) {
		super(node);
	}

	/** Accept the visitor. * */
	public Object jjtAccept(TransformLangParserVisitor visitor, Object data) {
		return visitor.visit(this, data);
	}

	@Override
	public SimpleNode duplicate() {
		return new CLVFPrintLogNode(this);
	}

}
