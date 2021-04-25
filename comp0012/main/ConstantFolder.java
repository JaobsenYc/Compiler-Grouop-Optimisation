package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;

import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.*;
import org.apache.bcel.util.InstructionFinder;
import org.apache.bcel.util.InstructionFinder.CodeConstraint;

public class ConstantFolder {
	private static class VariableFinder implements CodeConstraint {
		private int variableIndex;
		private int localVariableInstructionIndex;

		public VariableFinder(int variableIndex) {
			this.variableIndex = variableIndex;
			this.localVariableInstructionIndex = 0;
		}

		public VariableFinder(int variableIndex, int localVariableInstructionIndex) {
			this.variableIndex = variableIndex;
			this.localVariableInstructionIndex = localVariableInstructionIndex;
		}

		public boolean checkCode(InstructionHandle[] match) {
			LocalVariableInstruction instruction = (LocalVariableInstruction)(match[localVariableInstructionIndex].getInstruction());
			return instruction.getIndex() == variableIndex;
		}
	}

	private static class ConstantFinder implements CodeConstraint {
		private int constantIndex;
		private int constantInstructionIndex;

		public ConstantFinder(int constantIndex) {
			this.constantIndex = constantIndex;
			this.constantInstructionIndex = 0;
		}

		public ConstantFinder(int constantIndex, int constantInstructionIndex) {
			this.constantIndex = constantIndex;
			this.constantInstructionIndex = constantInstructionIndex;
		}

		public boolean checkCode(InstructionHandle[] match) {
			CPInstruction instruction = (CPInstruction)(match[constantInstructionIndex].getInstruction());
			return instruction.getIndex() == constantIndex;
		}
	}

	private static class Jump {
		protected int from, to;

		public Jump(int from, int to) {
			this.from = from;
			this.to = to;
		}

		public Jump(Jump copy) {
			this(copy.from, copy.to);
		}

		public int getFrom() {
			return from;
		}

		public int getTo() {
			return to;
		}

		public void setFrom(int value) {
			from = value;
		}

		public void setTo(int value) {
			to = value;
		}

		public boolean contains(int test) {
			int lo, hi;
			if(from < to) {
				lo = from;
				hi = to;
			} else {
				lo = to;
				hi = from;
			}

			return test >= lo && test <= hi;
		}

		public String toString() {
			return "Jump from " + from + " to " + to;
		}
	}

	private static class JumpManager {
		private ArrayList<Jump> jumps;
		private HashSet<Integer> origins;
		private HashSet<Integer> destinations;

		public JumpManager() {
			this.jumps = new ArrayList<Jump>();
			recomputeSets();
		}

		public ArrayList<Jump> getJumps() {
			return new ArrayList<Jump>(jumps);
		}

		public void addJump(Jump toAdd) {
			if(toAdd instanceof Jump) {
				jumps.add(toAdd);
				origins.add(toAdd.getFrom());
				destinations.add(toAdd.getTo());
			}
		}

		public void removeJump(Jump toRemove) {
			if(jumps.remove(toRemove))
				recomputeSets();
		}

		private void recomputeSets() {
			origins = new HashSet<Integer>();
			destinations = new HashSet<Integer>();
			for(Jump jump : jumps) {
				origins.add(jump.getFrom());
				destinations.add(jump.getTo());
			}
		}

		public ArrayList<Jump> jumpsContaining(int index) {
			ArrayList<Jump> matches = new ArrayList<Jump>();
			for(Jump jump : jumps)
				if(jump.contains(index))
					matches.add(new Jump(jump));
			return matches;
		}

		public boolean destinationsContain(int position) {
			for(int index : destinations)
				if(index == position)
					return true;
			return false;
		}

		public boolean destinationsContain(int[] positions) {
			for(int position : positions)
				if(destinationsContain(position))
					return true;
			return false;
		}

		public String toString() {
			return jumps.toString();
		}
	}

	private static class Range extends Jump {
		public Range(int from, int to) {
			super(from < to ? from : to, from < to ? to : from);
		}

		public String toString() {
			return "Range from " + from + " to " + to;
		}

		public boolean contains(int test) {
			return test >= from && test < to;
		}
	}

	private static class Variable {
		private final int index;
		private final ArrayList<Range> constantRanges;
		private final ArrayList<Object> values;

		private Variable(int index) {
			this.index = index;
			this.constantRanges = new ArrayList<Range>();
			this.values = new ArrayList<Object>();
		}

		public Variable(int index, ArrayList<Range> constantRanges) {
			this(index);
			for(Range range : constantRanges) {
				this.constantRanges.add(range);
				this.values.add(null);
			}
		}

		public int getIndex() {
			return index;
		}

		public Object getValueAtPosition(int position) {
			for(int index = 0 ; index < constantRanges.size() ; index++) {
				Range range = constantRanges.get(index);
				if(range.contains(position))
					return values.get(index);
			}
			return null;
		}

		public void setValueAtPosition(Object value, int position) {
			for(int index = 0 ; index < constantRanges.size() ; index++) {
				Range range = constantRanges.get(index);
				if(range.contains(position))
					values.set(index, value);
			}
		}

		public String toString() {
			String toReturn = String.format("Var #%d with %d constant value sections%c\n", index, constantRanges.size(), constantRanges.size() > 0 ? ':' : ' ');
			for(int index = 0 ; index < constantRanges.size() ; index++) {
				Range range = constantRanges.get(index);
				Object value = values.get(index);
				toReturn += String.format("\tvalue %s in %s\n", value, range);
			}
			return toReturn;
		}
	}

	private static class VariableManager {
		private HashMap<Integer, Variable> variables;

		public VariableManager() {
			this.variables = new HashMap<Integer, Variable>();
		}

		public VariableManager(ArrayList<Variable> variables) {
			this();
			for(Variable var : variables)
				this.variables.put(var.getIndex(), var);
		}

		public void addVariable(Variable toAdd) {
			variables.put(toAdd.getIndex(), toAdd);
		}

		public void removeVariable(Variable toRemove) {
			variables.remove(toRemove.getIndex());
		}

		public Variable getVariable(int index) {
			return variables.get(index);
		}

		public String toString() {
			return variables.values().toString();
		}

		public Object variableValueAtPosition(int variableIndex, int position) {
			Variable variable = variables.get(variableIndex);
			if(variable instanceof Variable) {
				Object value = variable.getValueAtPosition(position);
				if(value instanceof Object) {
					return value;
				}
			}
			return null;
		}
	}

	ClassParser parser = null;
	ClassGen gen = null;
	int debugLevel = 0;

	JavaClass original = null;
	JavaClass optimized = null;

	public ConstantFolder(String classFilePath) {
		try {
			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
		} catch(IOException e){
			e.printStackTrace();
		}
	}

	// Checked
	private JumpManager initializeJumpManagerWithJumps(InstructionList il) {
		debug("Finding all jumps", 1);

		JumpManager jumpManager = new JumpManager();
		InstructionFinder finder = new InstructionFinder(il);
		Iterator itr = finder.search("BranchInstruction");

		while(itr.hasNext()) {
			InstructionHandle[] instructions = (InstructionHandle[])itr.next();
			BranchInstruction branchInstruction = (BranchInstruction)instructions[0].getInstruction();

			if(branchInstruction instanceof Select) {
				Select selectInstruction = (Select)branchInstruction;
				for(int offset : selectInstruction.getIndices()) {
					jumpManager.addJump(new Jump(instructions[0].getPosition(), instructions[0].getPosition() + offset));
				}
			} else {
				jumpManager.addJump(new Jump(instructions[0].getPosition(), instructions[0].getPosition() + branchInstruction.getIndex()));
			}
		}

		debug(jumpManager.toString(), 2);

		return jumpManager;
	}

	// Finished
	private HashSet<Integer> getAllVariableIndices(InstructionList il) {
		debug("Extracting list of local variables", 1);
		HashSet<Integer> indices = new HashSet<Integer>();
		InstructionFinder finder = new InstructionFinder(il);
		Iterator itr = finder.search("StoreInstruction");
		while(itr.hasNext()) {
			InstructionHandle[] instructions = (InstructionHandle[])itr.next();
			StoreInstruction storeInstruction = (StoreInstruction)(instructions[0].getInstruction());
			indices.add(storeInstruction.getIndex());
		}
		debug(String.format("%d local variables: %s\n", indices.size(), indices), 3);
		return indices;
	}

	private int integerArrayListAddWrapper(ArrayList<Integer> list, int toAdd) {
		list.add(toAdd);
		return toAdd;
	}

	private void debug(String message, int level) {
		if(level <= debugLevel ) {
			System.out.println(message);
		}
	}

	// Returns true on optimization, false on no action
	private boolean removeUnreachableCode(InstructionList il, JumpManager jumpManager) {
		debug("Removing unreachable code (forward GOTOs)", 1);
		boolean performedOptimization = false;
		InstructionFinder finder = new InstructionFinder(il);
		Iterator itr = finder.search("GotoInstruction");
		if(itr.hasNext()) {
			InstructionHandle[] instructions = (InstructionHandle[])itr.next();
			GotoInstruction gotoInstruction = (GotoInstruction)(instructions[0].getInstruction());

			if(gotoInstruction.getTarget().getPosition() - instructions[0].getPosition() > 0) {
				// forward goto => check
				boolean codeReachable = false;
				InstructionHandle current = instructions[0].getNext();
				while(!current.equals(gotoInstruction.getTarget())) {
					ArrayList<Jump> jumps = jumpManager.getJumps();
					for(Jump jump : jumps) {
						if(jump.getTo() == current.getPosition() && (jump.getFrom() < instructions[0].getPosition() || jump.getFrom() >= gotoInstruction.getTarget().getPosition())) {
							codeReachable = true;
							break;
						}
					}

					if(codeReachable)
						break;

					current = current.getNext();
				}

				if(!codeReachable) {
					performedOptimization = true;
					try {
						il.delete(instructions[0], gotoInstruction.getTarget().getPrev());
					} catch(TargetLostException e) { }

					debug("Removed unreachable code:", 3);
					debug(il.toString(), 3);
				}
			}
		}
		return performedOptimization;
	}

	private VariableManager initializeVariableManagerWithConstantSections(InstructionList il, JumpManager jumpManager, HashSet<Integer> localVariableIndices) {
		debug("Determining constant sections of local variables", 1);
		VariableManager variableManager = new VariableManager();
		InstructionFinder finder = new InstructionFinder(il);
		for(int varIndex : localVariableIndices) {
			Iterator itr = finder.search("StoreInstruction | iinc", new VariableFinder(varIndex, 0));
			ArrayList<Integer> positions = new ArrayList<Integer>();
			while(itr.hasNext()) {
				InstructionHandle[] instructions = (InstructionHandle[])itr.next();
				positions.add(instructions[0].getPosition());
			}

			ArrayList<Range> ranges = new ArrayList<Range>();
			Range current = new Range(positions.get(0), positions.get(0));
			boolean shouldAdd = true;
			for(int changePosIndex = 1 ; changePosIndex < positions.size() && shouldAdd ; changePosIndex++) {
				int changePosition = positions.get(changePosIndex);
				current.setTo(changePosition);
				ArrayList<Jump> jumpsContaining = jumpManager.jumpsContaining(changePosition);
				if(jumpsContaining.size() > 0) {
					int lowestStart = changePosition;
					for(Jump jump : jumpsContaining) {
						if(jump.getFrom() < lowestStart)
							lowestStart = jump.getFrom();
						if(jump.getTo() < lowestStart)
							lowestStart = jump.getTo();
					}
					current.setTo(lowestStart);
					shouldAdd = false;
				}

				Range toAdd = new Range(current.getFrom(), current.getTo());
				if(toAdd.getTo() - 1 > toAdd.getFrom())
					ranges.add(toAdd);
				current.setFrom(changePosition);
			}
			if(shouldAdd) {
				current.to = il.getEnd().getPosition() + 1;
				ranges.add(new Range(current.from, current.to));
			}

			variableManager.addVariable(new Variable(varIndex, ranges));
		}
		debug(variableManager.toString(), 3);

		return variableManager;
	}

	private Object getValueOfPushInstruction(InstructionHandle instruction, ConstantPoolGen cpgen, VariableManager variableManager) {
		PushInstruction pushInstruction = (PushInstruction)instruction.getInstruction();
		int position = instruction.getPosition();

		Object value = null;
		if(pushInstruction instanceof ConstantPushInstruction) {
			ConstantPushInstruction constantPushInstruction = (ConstantPushInstruction)pushInstruction;
			value = constantPushInstruction.getValue();
		} else if(pushInstruction instanceof LoadInstruction) {
			LoadInstruction loadInstruction = (LoadInstruction)pushInstruction;
			int variableIndex = loadInstruction.getIndex();
			value = variableManager.variableValueAtPosition(variableIndex, position);
		} else if(pushInstruction instanceof LDC) {
			LDC constantLoadInstruction = (LDC)pushInstruction;
			value = constantLoadInstruction.getValue(cpgen);
		} else if(pushInstruction instanceof LDC2_W) {
			LDC2_W constantLoadInstruction = (LDC2_W)pushInstruction;
			value = constantLoadInstruction.getValue(cpgen);
		}

		if(value instanceof Number)
			return value;
		else
			return null;
	}

	private void updateVariableValues(InstructionList il, ConstantPoolGen cpgen, JumpManager jumpManager, VariableManager variableManager) {
		debug("Redetermining local variable values", 1);
		InstructionFinder finder = new InstructionFinder(il);
		Iterator itr = finder.search("PushInstruction StoreInstruction");
		while(itr.hasNext()) {
			InstructionHandle[] instructions = (InstructionHandle[])itr.next();

			if(jumpManager.destinationsContain(instructions[1].getPosition())) {
				debug("Skipping 'PushInstruction StoreInstruction' match across jump destination", 2);
				continue;
			}

			PushInstruction pushInstruction = (PushInstruction)instructions[0].getInstruction();
			StoreInstruction storeInstruction = (StoreInstruction)instructions[1].getInstruction();

			Variable variable = variableManager.getVariable(storeInstruction.getIndex());
			int loadPosition = instructions[0].getPosition();
			int storePosition = instructions[1].getPosition();

			// found a constant variable, add to dictionary
			Object constant = getValueOfPushInstruction(instructions[0], cpgen, variableManager);
			if(constant instanceof Object) {
				variable.setValueAtPosition(constant, storePosition);
			}
		}
		debug(variableManager.toString(), 2);
	}

	// Returns true on optimization, false on no action
	private boolean foldBinaryArithmeticOperations(InstructionList il, ConstantPoolGen cpgen, JumpManager jumpManager, VariableManager variableManager) {
		debug("Folding binary arithmetic operators", 1);
		InstructionFinder finder = new InstructionFinder(il);
		Iterator itr = finder.search("PushInstruction PushInstruction (ArithmeticInstruction|lcmp|dcmpg|dcmpl|fcmpg|fcmpl)");
		boolean performedFolding = false;
		while(itr.hasNext()) {
			InstructionHandle[] instructions = (InstructionHandle[])itr.next();
			debug(Arrays.toString(instructions), 3);

			if(jumpManager.destinationsContain(new int[]{instructions[1].getPosition(), instructions[2].getPosition()})) {
				debug("Skipping 'PushInstruction PushInstruction ArithmeticInstruction' match across jump destination", 2);
				continue;
			}

			Object[] operands = new Object[]{null, null};
			for( int j = 0 ; j < 2 ; j++ )
				operands[j] = getValueOfPushInstruction(instructions[j], cpgen, variableManager);

			if(operands[0] == null || operands[1] == null)
				continue;

			Number operandA = (Number)operands[0];
			Number operandB = (Number)operands[1];

			Instruction foldedInstruction = null;
			Instruction operation = instructions[2].getInstruction();
			if(operation instanceof IADD) {
				foldedInstruction = new LDC(cpgen.addInteger(operandA.intValue() + operandB.intValue()));
			} else if(operation instanceof FADD) {
				foldedInstruction = new LDC(cpgen.addFloat(operandA.floatValue() + operandB.floatValue()));
			} else if(operation instanceof DADD) {
				foldedInstruction = new LDC2_W(cpgen.addDouble(operandA.doubleValue() + operandB.doubleValue()));
			} else if(operation instanceof LADD) {
				foldedInstruction = new LDC2_W(cpgen.addLong(operandA.longValue() + operandB.longValue()));
			} else if(operation instanceof ISUB) {
				foldedInstruction = new LDC(cpgen.addInteger(operandA.intValue() - operandB.intValue()));
			} else if(operation instanceof FSUB) {
				foldedInstruction = new LDC(cpgen.addFloat(operandA.floatValue() - operandB.floatValue()));
			} else if(operation instanceof DSUB) {
				foldedInstruction = new LDC2_W(cpgen.addDouble(operandA.doubleValue() - operandB.doubleValue()));
			} else if(operation instanceof LSUB) {
				foldedInstruction = new LDC2_W(cpgen.addLong(operandA.longValue() - operandB.longValue()));
			} else if(operation instanceof IMUL) {
				foldedInstruction = new LDC(cpgen.addInteger(operandA.intValue() * operandB.intValue()));
			} else if(operation instanceof FMUL) {
				foldedInstruction = new LDC(cpgen.addFloat(operandA.floatValue() * operandB.floatValue()));
			} else if(operation instanceof DMUL) {
				foldedInstruction = new LDC2_W(cpgen.addDouble(operandA.doubleValue() * operandB.doubleValue()));
			} else if(operation instanceof LMUL) {
				foldedInstruction = new LDC2_W(cpgen.addLong(operandA.longValue() * operandB.longValue()));
			} else if(operation instanceof IDIV) {
				foldedInstruction = new LDC(cpgen.addInteger(operandA.intValue() / operandB.intValue()));
			} else if(operation instanceof FDIV) {
				foldedInstruction = new LDC(cpgen.addFloat(operandA.floatValue() / operandB.floatValue()));
			} else if(operation instanceof DDIV) {
				foldedInstruction = new LDC2_W(cpgen.addDouble(operandA.doubleValue() / operandB.doubleValue()));
			} else if(operation instanceof LDIV) {
				foldedInstruction = new LDC2_W(cpgen.addLong(operandA.longValue() / operandB.longValue()));
			} else if(operation instanceof LCMP) {
				long a = operandA.longValue();
				long b = operandB.longValue();
				foldedInstruction = new ICONST(a < b ? -1 : (a == b ? 0 : 1));
			} else if(operation instanceof DCMPG) {
				double a = operandA.doubleValue();
				double b = operandB.doubleValue();
				if(Double.isNaN(a) || Double.isNaN(b))
					foldedInstruction = new ICONST(1);
				else
					foldedInstruction = new ICONST(a < b ? -1 : (a == b ? 0 : 1));
			} else if(operation instanceof DCMPL) {
				double a = operandA.doubleValue();
				double b = operandB.doubleValue();
				if(Double.isNaN(a) || Double.isNaN(b))
					foldedInstruction = new ICONST(-1);
				else
					foldedInstruction = new ICONST(a < b ? -1 : (a == b ? 0 : 1));
			} else if(operation instanceof FCMPG) {
				float a = operandA.floatValue();
				float b = operandB.floatValue();
				if(Float.isNaN(a) || Float.isNaN(b))
					foldedInstruction = new ICONST(-1);
				else
					foldedInstruction = new ICONST(a < b ? -1 : (a == b ? 0 : 1));
			} else if(operation instanceof FCMPL) {
				float a = operandA.floatValue();
				float b = operandB.floatValue();
				if(Float.isNaN(a) || Float.isNaN(b))
					foldedInstruction = new ICONST(-1);
				else
					foldedInstruction = new ICONST(a < b ? -1 : (a == b ? 0 : 1));
			}

			// insert new stack push instruction
			if(foldedInstruction != null) {
				performedFolding = true;
				instructions[0].setInstruction(foldedInstruction);

				// remove stack push instructions
				try {
					il.delete(instructions[1]);
					il.delete(instructions[2]);
				} catch(TargetLostException e) { }
			}
		}

		return performedFolding;
	}

	// Returns true on optimization, false on no action
	private boolean resolveBinaryComparisonBranches(InstructionList il, ConstantPoolGen cpgen, JumpManager jumpManager, VariableManager variableManager) {
		debug("Resolving binary comparison branch instructions", 1);
		InstructionFinder finder = new InstructionFinder(il);
		Iterator itr = finder.search("PushInstruction PushInstruction IfInstruction");
		boolean performedOptimization = false;
		while(itr.hasNext()) {
			InstructionHandle[] instructions = (InstructionHandle[])itr.next();

			if(jumpManager.destinationsContain(new int[]{instructions[1].getPosition(), instructions[2].getPosition()})) {
				debug("Skipping 'PushInstruction PushInstruction IfInstruction' match across jump destination", 2);
				continue;
			}

			Object[] operands = new Object[]{null, null};
			for( int j = 0 ; j < 2 ; j++ )
				operands[j] = getValueOfPushInstruction(instructions[j], cpgen, variableManager);

			if(operands[0] == null || operands[1] == null)
				continue;

			Integer operandA = (Integer)operands[0];
			Integer operandB = (Integer)operands[1];

			boolean branch = false;
			boolean canHandle = true;
			IfInstruction ifInstruction = (IfInstruction)(instructions[2].getInstruction());
			if(ifInstruction instanceof IF_ICMPEQ) {
				branch = operandA.equals(operandB);
			} else if(ifInstruction instanceof IF_ICMPNE) {
				branch = !operandA.equals(operandB);
			} else if(ifInstruction instanceof IF_ICMPLT) {
				branch = operandA.compareTo(operandB) < 0;
			} else if(ifInstruction instanceof IF_ICMPLE) {
				branch = operandA.compareTo(operandB) <= 0;
			} else if(ifInstruction instanceof IF_ICMPGT) {
				branch = operandA.compareTo(operandB) > 0;
			} else if(ifInstruction instanceof IF_ICMPGE) {
				branch = operandA.compareTo(operandB) >= 0;
			} else {
				canHandle = false;
			}

			if(canHandle) {
				performedOptimization = true;

				if(branch) {
					instructions[2].setInstruction(new GOTO(ifInstruction.getTarget()));
					il.redirectBranches(instructions[0], instructions[2]);
					try {
						il.delete(instructions[0], instructions[1]);
					} catch(TargetLostException e) { }
				} else {
					il.redirectBranches(instructions[0], instructions[2].getNext());
					try {
						il.delete(instructions[0], instructions[2]);
					} catch(TargetLostException e) { }
				}
			}
		}
		return performedOptimization;
	}

	// Returns true on optimization, false on no action
	private boolean resolveUnaryComparisonBranches(InstructionList il, ConstantPoolGen cpgen, JumpManager jumpManager, VariableManager variableManager) {
		debug("Resolving unary comparison (against 0) branch instructions", 1);
		InstructionFinder finder = new InstructionFinder(il);
		Iterator itr = finder.search("PushInstruction IfInstruction");
		boolean performedOptimization = false;
		while(itr.hasNext()) {
			InstructionHandle[] instructions = (InstructionHandle[])itr.next();

			if(jumpManager.destinationsContain(instructions[1].getPosition())) {
				debug("Skipping 'PushInstruction IfInstruction' match across jump destination", 2);
				continue;
			}

			Object operand = getValueOfPushInstruction(instructions[0], cpgen, variableManager);
			if(operand == null)
				continue;

			Integer realOperand = (Integer)operand;

			boolean branch = false;
			boolean canHandle = true;
			IfInstruction ifInstruction = (IfInstruction)(instructions[1].getInstruction());
			if(ifInstruction instanceof IFEQ) {
				branch = realOperand.equals(0);
			} else if(ifInstruction instanceof IFNE) {
				branch = !realOperand.equals(0);
			} else if(ifInstruction instanceof IFLT) {
				branch = realOperand.compareTo(0) < 0;
			} else if(ifInstruction instanceof IFLE) {
				branch = realOperand.compareTo(0) <= 0;
			} else if(ifInstruction instanceof IFGT) {
				branch = realOperand.compareTo(0) > 0;
			} else if(ifInstruction instanceof IFGE) {
				branch = realOperand.compareTo(0) >= 0;
			} else {
				canHandle = false;
			}

			if(canHandle) {
				performedOptimization = true;

				if(branch) {
					instructions[1].setInstruction(new GOTO(ifInstruction.getTarget()));
					il.redirectBranches(instructions[0], instructions[1]);
					try {
						il.delete(instructions[0]);
					} catch(TargetLostException e) { }
				} else {
					il.redirectBranches(instructions[0], instructions[1].getNext());
					try {
						il.delete(instructions[0], instructions[1]);
					} catch(TargetLostException e) { }
				}
			}
		}

		return performedOptimization;
	}

	// Returns true on optimization, false on no action
	private boolean removeUnusedVariables(InstructionList il, HashSet<Integer> localVariableIndices, JumpManager jumpManager) {
		debug("Removing unused variables from instruction list", 1);
		boolean performedOptimization = false;
		for(int varIndex : localVariableIndices) {
			InstructionFinder finder = new InstructionFinder(il);
			Iterator itr = finder.search("LoadInstruction", new VariableFinder(varIndex, 0));
			if(!itr.hasNext()) {
				// unused variable
				itr = finder.search("PushInstruction StoreInstruction", new VariableFinder(varIndex, 1));
				while(itr.hasNext()) {
					InstructionHandle[] instructions = (InstructionHandle[])itr.next();

					if(jumpManager.destinationsContain(instructions[1].getPosition())) {
						debug("Skipping 'PushInstruction Store' match across jump destination", 2);
						continue;
					}

					performedOptimization = true;

					il.redirectBranches(instructions[0], instructions[1].getNext());
					try {
						il.delete(instructions[0], instructions[1]);
					} catch(TargetLostException e) { }
				}
			}
		}
		return performedOptimization;
	}

	// Returns true on optimization, false on no action
	private boolean foldTypeConversions(InstructionList il, ConstantPoolGen cpgen, JumpManager jumpManager, VariableManager variableManager) {
		debug("Folding type conversions", 1);
		InstructionFinder finder = new InstructionFinder(il);
		Iterator itr = finder.search("PushInstruction ConversionInstruction");
		boolean performedFolding = false;
		while(itr.hasNext()) {
			InstructionHandle[] instructions = (InstructionHandle[])itr.next();

			if(jumpManager.destinationsContain(instructions[1].getPosition())) {
				debug("Skipping 'PushInstruction ConversionInstruction' match across jump destination", 2);
				continue;
			}

			Object value = getValueOfPushInstruction(instructions[0], cpgen, variableManager);
			if(value == null)
				continue;

			Number operand = (Number)value;
			Instruction foldedInstruction = null;
			ConversionInstruction instruction = (ConversionInstruction)instructions[1].getInstruction();
			if(instruction instanceof D2F || instruction instanceof I2F || instruction instanceof L2F) {
				foldedInstruction = new LDC(cpgen.addFloat(operand.floatValue()));
			} else if(instruction instanceof I2D || instruction instanceof L2D || instruction instanceof F2D) {
				foldedInstruction = new LDC2_W(cpgen.addDouble(operand.doubleValue()));
			} else if(instruction instanceof D2L || instruction instanceof F2L || instruction instanceof I2L) {
				foldedInstruction = new LDC2_W(cpgen.addLong(operand.longValue()));
			} else if(instruction instanceof D2I || instruction instanceof F2I || instruction instanceof L2I) {
				foldedInstruction = new LDC(cpgen.addInteger(operand.intValue()));
			}

			// insert new stack push instruction
			if(foldedInstruction != null) {
				performedFolding = true;
				instructions[0].setInstruction(foldedInstruction);

				// remove stack push instructions
				il.redirectBranches(instructions[0], instructions[1].getNext());
				try {
					il.delete(instructions[1]);
				} catch(TargetLostException e) { }
			}
		}

		return performedFolding;
	}

	// Returns true on optimization, false on no action
	private boolean foldNegations(InstructionList il, ConstantPoolGen cpgen, JumpManager jumpManager, VariableManager variableManager) {
		debug("Folding negations", 1);
		InstructionFinder finder = new InstructionFinder(il);
		Iterator itr = finder.search("PushInstruction ArithmeticInstruction");
		boolean performedOptimization = false;
		while(itr.hasNext()) {
			InstructionHandle[] instructions = (InstructionHandle[])itr.next();

			if(jumpManager.destinationsContain(instructions[1].getPosition())) {
				debug("Skipping 'PushInstruction ArithmeticInstruction' match across jump destination", 2);
				continue;
			}

			Object operand = getValueOfPushInstruction(instructions[0], cpgen, variableManager);
			if(operand == null)
				continue;

			Number realOperand = (Number)operand;
			Instruction operation = (Instruction)instructions[1].getInstruction();
			Instruction foldedInstruction = null;
			if(operation instanceof FNEG) {
				foldedInstruction = new LDC(cpgen.addFloat(-realOperand.floatValue()));
			} else if(operation instanceof DNEG) {
				foldedInstruction = new LDC2_W(cpgen.addDouble(-realOperand.doubleValue()));
			} else if(operation instanceof LNEG) {
				foldedInstruction = new LDC2_W(cpgen.addLong(-realOperand.longValue()));
			} else if(operation instanceof INEG) {
				foldedInstruction = new LDC(cpgen.addInteger(-realOperand.intValue()));
			}

			// insert new stack push instruction
			if(foldedInstruction != null) {
				performedOptimization = true;
				instructions[0].setInstruction(foldedInstruction);

				// remove stack push instructions
				try {
					il.delete(instructions[1]);
				} catch(TargetLostException e) { }
			}
		}


		return performedOptimization;
	}

	private Method optimizeMethod(ClassGen cgen, Method m) {
		ConstantPoolGen cpgen = cgen.getConstantPool();
		MethodGen mg = new MethodGen(m, cgen.getClassName(), cpgen);
		mg.removeNOPs();

		InstructionList il = mg.getInstructionList();

		debug(String.format("\nOptimizing METHOD %s", mg.getName()), 1);
		debug("__before__", 2);
		debug(il.toString(), 2);

		HashSet<Integer> localVariableIndices;
		JumpManager jumpManager;
		VariableManager variableManager;
		boolean performedOptimization;
		do {
			performedOptimization = false;

			/* FIND ALL JUMP POINTS */
			jumpManager = initializeJumpManagerWithJumps(il);

			/* REMOVE UNREACHABLE CODE */
			if(removeUnreachableCode(il, jumpManager)) {
				performedOptimization = true;
				jumpManager = initializeJumpManagerWithJumps(il);
			}

			/* GET VARIABLE INDICES */
			localVariableIndices = getAllVariableIndices(il);

			/* DETERMINE CONSTANT SECTIONS OF VARIABLE */
			variableManager = initializeVariableManagerWithConstantSections(il, jumpManager, localVariableIndices);

			/* ALL FOLDING */
			boolean performedFolding;
			do {
				debug("In constant folding loop", 1);
				performedFolding = false;

				/* UPDATE CONSTANT VARIABLES VALUES */
				updateVariableValues(il, cpgen, jumpManager, variableManager);

				/* PERFORM TYPE CONVERSIONS */
				if(foldTypeConversions(il, cpgen, jumpManager, variableManager))
					performedFolding = performedOptimization = true;

				/* FOLD NEGATIONS */
				if(foldNegations(il, cpgen, jumpManager, variableManager))
					performedFolding = performedOptimization = true;

				/* FOLD BINARY ARITHMETIC OPERATIONS */
				if(foldBinaryArithmeticOperations(il, cpgen, jumpManager, variableManager))
					performedFolding = performedOptimization = true;

				debug(il.toString(), 3);
				debug(cpgen.toString(), 3);
			} while(performedFolding);

			/* RESOLVE BINARY COMPARISONS */
			if(resolveBinaryComparisonBranches(il, cpgen, jumpManager, variableManager))
				performedOptimization = true;

			/* RESOLVE UNARY COMPARISONS */
			if(resolveUnaryComparisonBranches(il, cpgen, jumpManager, variableManager))
				performedOptimization = true;

			debug(il.toString(), 3);

			/* REMOVE UNUSED VARIABLES FROM INSTRUCTION LIST */
			if(removeUnusedVariables(il, localVariableIndices, jumpManager))
				performedOptimization = true;

			// il.setPositions();

			debug(il.toString(), 3);
		} while(performedOptimization);

		debug("__after__", 2);
		debug(il.toString(), 2);

		mg.stripAttributes(true);
		mg.setMaxStack();
		return mg.getMethod();
	}

	private void optimize() {
		ClassGen cgen = new ClassGen(original);
		ConstantPoolGen cpgen = cgen.getConstantPool();
		debug(String.format("\n\nOptimizing CLASS %s", cgen.getClassName()), 1);

		Method[] methods = cgen.getMethods();
		Method[] optimizedMethods = new Method[methods.length];
		for( int index = 0 ; index < methods.length ; index++ )
			optimizedMethods[index] = optimizeMethod(cgen, methods[index]);

		debug(cpgen.toString(), 2);

		this.gen.setMethods(optimizedMethods);
		this.gen.setConstantPool(cpgen);
		this.gen.setMajor(50);
		this.optimized = gen.getJavaClass();
	}

	public void write(String optimisedFilePath)
	{
		this.optimize();

		try {
			FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
			this.optimized.dump(out);
		} catch (FileNotFoundException e) {
			// Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
	}
}