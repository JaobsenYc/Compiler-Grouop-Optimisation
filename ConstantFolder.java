package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;


public class ConstantFolder
{
	ClassParser parser = null;
	ClassGen gen = null;

	JavaClass original = null;
	JavaClass optimized = null;

	public ConstantFolder(String classFilePath)
	{
		try{
			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
		} catch(IOException e){
			e.printStackTrace();
		}
	}

	public void optimize()
	{
		ClassGen cgen = new ClassGen(original);
		ConstantPoolGen cpgen = cgen.getConstantPool();
		cgen.setMajor(50);
		Method[] methods = cgen.getMethods();
		for (Method method : methods) {
			optimizeMethod(cgen, cpgen, method);
		}
		this.optimized = cgen.getJavaClass();
	}

	private void optimizeMethod(ClassGen cgen, ConstantPoolGen cpgen, Method method) {
		MethodGen mgen = new MethodGen(method, cgen.getClassName(), cpgen);
		InstructionList instList = mgen.getInstructionList();

		simpleFolding(cgen, cpgen, method, instList);

		Method newM = mgen.getMethod();
		cgen.replaceMethod(method, newM);
	}

	private void simpleFolding(ClassGen cgen, ConstantPoolGen cpgen, Method method, InstructionList instList) {
		System.out.println("Performing Simple Folding On > " + cgen.getClassName() + " > " + method.getName());
		boolean optimised = true;
		while (optimised){
			optimised = false;
			String regex = "(LDC|LDC2_W|ConstantPushInstruction) (LDC|LDC2_W|ConstantPushInstruction) ArithmeticInstruction";
			InstructionFinder finder = new InstructionFinder(instList);
			Iterator iterator = finder.search(regex);
			while (iterator.hasNext()) {//...}
				InstructionHandle[] instructions = (InstructionHandle[]) iterator.next();
				int instNum = 0;

				Number Num1 = getNum(instNum, cpgen, instructions);
				instNum += 1;

				Number Num2 = getNum(instNum, cpgen, instructions);
				instNum += 1;

				ArithmeticInstruction operation = getOperation(instNum, instructions);
				Type numType = operation.getType(cpgen);
				String opType = operation.getName().substring(1);

				Number foldedValue = doOperation(Num1, Num2, numType, opType);
				if (foldedValue != null) {
					int constantPoolIndex = addToConstantPool(numType, foldedValue, cpgen);
					if (constantPoolIndex != -1) {
						System.out.println("Simple Folding Success!");
						System.out.println("Before: ");
						System.out.println(instList);
						InstructionHandle newInst = addInst(numType, instList, instructions[0], constantPoolIndex);
						deleteInst(instList, instructions[0], instructions[instNum], newInst);
						optimised = true;
						System.out.println("After: ");
						System.out.println(instList);
					}
				}
			}
		}
	}

	private Number getNum(int instNum, ConstantPoolGen cpgen, InstructionHandle[] instructions) {
		Number Num = null;
		if (instructions[instNum].getInstruction() instanceof LDC) {
			Num = (Number) ((LDC) instructions[instNum].getInstruction()).getValue(cpgen);
		}
		if (instructions[instNum].getInstruction() instanceof LDC2_W) {
			Num = ((LDC2_W) instructions[instNum].getInstruction()).getValue(cpgen);
		}
		if (instructions[instNum].getInstruction() instanceof ConstantPushInstruction) {
			Num = ((ConstantPushInstruction) instructions[instNum].getInstruction()).getValue();
		}
		return Num;
	}

	private ArithmeticInstruction getOperation(int instNum, InstructionHandle[] instructions) {
		ArithmeticInstruction operation = null;
		if (instructions[instNum].getInstruction() instanceof ArithmeticInstruction) {
			operation = (ArithmeticInstruction) instructions[instNum].getInstruction();
		}
		return operation;
	}

	private int addToConstantPool(Type numType, Number foldedValue, ConstantPoolGen cpgen) {
		int constantPoolIndex = -1;
		if (numType == Type.INT) {
			constantPoolIndex = cpgen.addInteger(foldedValue.intValue());
		}
		if (numType == Type.LONG) {
			constantPoolIndex = cpgen.addLong(foldedValue.longValue());
		}
		if (numType == Type.FLOAT) {
			constantPoolIndex = cpgen.addFloat(foldedValue.floatValue());
		}
		if (numType == Type.DOUBLE) {
			constantPoolIndex = cpgen.addDouble(foldedValue.doubleValue());
		}
		return constantPoolIndex;
	}

	private InstructionHandle addInst(Type numType, InstructionList instList, InstructionHandle position, int constantPoolIndex) {
		InstructionHandle newInst = null;
		boolean cond1 = (numType == Type.INT || numType == Type.FLOAT);
		boolean cond2 = (numType == Type.LONG || numType == Type.DOUBLE);
		if (cond1) {
			newInst = instList.insert(position, new LDC(constantPoolIndex));
		} else if (cond2) {
			newInst = instList.insert(position, new LDC2_W(constantPoolIndex));
		}
		return newInst;
	}

	private void deleteInst(InstructionList instList, InstructionHandle first, InstructionHandle last, InstructionHandle newInst) {
		try {
			instList.delete(first, last);
		} catch (TargetLostException e) {
			for (InstructionHandle target : e.getTargets()) {
				for (InstructionTargeter targeter : target.getTargeters()) {
					targeter.updateTarget(target, newInst);
				}
			}
		}
	}

	private Number doOperation(Number Num1, Number Num2, Type numType, String opType) {
		Number result = null;
		switch (opType) {
			case "add":
				result = add(Num1, Num2, numType);
				break;
			case "sub":
				result = sub(Num1, Num2, numType);
				break;
			case "mul":
				result = mul(Num1, Num2, numType);
				break;
			case "div":
				result = div(Num1, Num2, numType);
				break;
		}
		return result;
	}

	private Number add(Number Num1, Number Num2, Type numType) {
		Number result = null;
		if (numType == Type.INT) {
			result = Num1.intValue() + Num2.intValue();
		}
		if (numType == Type.LONG) {
			result = Num1.longValue() + Num2.longValue();
		}
		if (numType == Type.FLOAT) {
			result = Num1.floatValue() + Num2.floatValue();
		}
		if (numType == Type.DOUBLE) {
			result = Num1.doubleValue() + Num2.doubleValue();
		}
		return result;
	}

	private Number sub(Number Num1, Number Num2, Type numType) {
		Number result = null;
		if (numType == Type.INT) {
			result = Num1.intValue() - Num2.intValue();
		}
		if (numType == Type.LONG) {
			result = Num1.longValue() - Num2.longValue();
		}
		if (numType == Type.FLOAT) {
			result = Num1.floatValue() - Num2.floatValue();
		}
		if (numType == Type.DOUBLE) {
			result = Num1.doubleValue() - Num2.doubleValue();
		}
		return result;
	}

	private Number mul(Number Num1, Number Num2, Type numType) {
		Number result = null;
		if (numType == Type.INT) {
			result = Num1.intValue() * Num2.intValue();
		}
		if (numType == Type.LONG) {
			result = Num1.longValue() * Num2.longValue();
		}
		if (numType == Type.FLOAT) {
			result = Num1.floatValue() * Num2.floatValue();
		}
		if (numType == Type.DOUBLE) {
			result = Num1.doubleValue() * Num2.doubleValue();
		}
		return result;
	}

	private Number div(Number Num1, Number Num2, Type numType) {
		Number result = null;
		if (numType == Type.INT) {
			result = Num1.intValue() / Num2.intValue();
		}
		if (numType == Type.LONG) {
			result = Num1.longValue() / Num2.longValue();
		}
		if (numType == Type.FLOAT) {
			result = Num1.floatValue() / Num2.floatValue();
		}
		if (numType == Type.DOUBLE) {
			result = Num1.doubleValue() / Num2.doubleValue();
		}
		return result;
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
