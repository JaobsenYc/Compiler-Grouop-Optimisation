package comp0012.main;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;


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

		// Implement your optimization here
		Method[] methods = cgen.getMethods();
		for (Method method : methods) {
			optimizeMethod(cgen, cpgen, method);
		}

		this.optimized = gen.getJavaClass();
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
			String regex = "(LDC|LDC2_W) (LDC|LDC2_W) ArithmeticInstruction";
			InstructionFinder finder = new InstructionFinder(instList);
			Iterator iterator = finder.search(regex);
			while (iterator.hasNext()) {
				InstructionHandle[] instructions = (InstructionHandle[]) iterator.next();
				int instNum = 0;

				Number Num1 = getNum(instNum, cpgen, instructions);
				instNum += 1;

				Number Num2 = getNum(instNum, cpgen, instructions);
				instNum += 1;

				ArithmeticInstruction operator = getOperator(instNum, instructions);
				Type numType = operator.getType(cpgen);
				String opType = operator.getName().substring(1);

				Number foldedValue = doOperation(Num1, Num2, numType, opType);
				if (foldedValue != null) {
					int constantPoolIndex = getCpIndex(numType, foldedValue, cpgen);
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
		} else if (instructions[instNum].getInstruction() instanceof LDC2_W) {
			Num = ((LDC2_W) instructions[instNum].getInstruction()).getValue(cpgen);
		}
		return Num;
	}

	private ArithmeticInstruction getOperator(int instNum, InstructionHandle[] instructions) {
		ArithmeticInstruction operator = null;
		if (instructions[instNum].getInstruction() instanceof ArithmeticInstruction) {
			operator = (ArithmeticInstruction) instructions[instNum].getInstruction();
		}
		return operator;
	}

	private int getCpIndex(Type numType, Number foldedValue, ConstantPoolGen cpgen) {
		int constantPoolIndex = -1;
		if (numType == Type.INT) {
			constantPoolIndex = cpgen.addInteger(foldedValue.intValue());
		} else if (numType == Type.FLOAT) {
			constantPoolIndex = cpgen.addFloat(foldedValue.floatValue());
		} else if (numType == Type.LONG) {
			constantPoolIndex = cpgen.addLong(foldedValue.longValue());
		} else if (numType == Type.DOUBLE) {
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
					if (newInst != null) {
						targeter.updateTarget(target, newInst);
					}
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
		} else if (numType == Type.LONG) {
			result = Num1.longValue() + Num2.longValue();
		} else if (numType == Type.FLOAT) {
			result = Num1.floatValue() + Num2.floatValue();
		} else if (numType == Type.DOUBLE) {
			result = Num1.doubleValue() + Num2.doubleValue();
		}
		return result;
	}

	private Number sub(Number Num1, Number Num2, Type numType) {
		Number result = null;
		if (numType == Type.INT) {
			result = Num1.intValue() - Num2.intValue();
		} else if (numType == Type.LONG) {
			result = Num1.longValue() - Num2.longValue();
		} else if (numType == Type.FLOAT) {
			result = Num1.floatValue() - Num2.floatValue();
		} else if (numType == Type.DOUBLE) {
			result = Num1.doubleValue() - Num2.doubleValue();
		}
		return result;
	}

	private Number mul(Number Num1, Number Num2, Type numType) {
		Number result = null;
		if (numType == Type.INT) {
			result = Num1.intValue() * Num2.intValue();
		} else if (numType == Type.LONG) {
			result = Num1.longValue() * Num2.longValue();
		} else if (numType == Type.FLOAT) {
			result = Num1.floatValue() * Num2.floatValue();
		} else if (numType == Type.DOUBLE) {
			result = Num1.doubleValue() * Num2.doubleValue();
		}
		return result;
	}

	private Number div(Number Num1, Number Num2, Type numType) {
		Number result = null;
		if (numType == Type.INT) {
			result = Num1.intValue() / Num2.intValue();
		} else if (numType == Type.LONG) {
			result = Num1.longValue() / Num2.longValue();
		} else if (numType == Type.FLOAT) {
			result = Num1.floatValue() / Num2.floatValue();
		} else if (numType == Type.DOUBLE) {
			result = Num1.doubleValue() / Num2.doubleValue();
		}
		return result;
	}

	public void printInstructions() {
		ClassGen cgen = new ClassGen(original);
		ConstantPoolGen cpgen = cgen.getConstantPool();
		Method[] methods = cgen.getMethods();
		for (Method method : methods) {
			MethodGen mgen = new MethodGen(method, cgen.getClassName(), cpgen);
			System.out.println(cgen.getClassName() + " > " + method.getName());
			System.out.println(mgen.getInstructionList());
		}
	}
	
	public void write(String optimisedFilePath)
	{
		this.optimize();

		try {
			FileOutputStream out = new FileOutputStream(optimisedFilePath);
			this.optimized.dump(out);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}