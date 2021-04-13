package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.HashMap;

import org.apache.bcel.classfile.*;
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


	private void simpleFolding(ClassGen cgen, ConstantPoolGen cpgen, Method method, InstructionList instList) {
		System.out.println("Performing Simple Folding On > " + cgen.getClassName() + " > " + method.getName());
		boolean optimised = true;
		while (optimised){
			optimised = false;
			String regex = "(LDC|LDC2_W|ConstantPushInstruction) ConversionInstruction? (LDC|LDC2_W|ConstantPushInstruction) ConversionInstruction? ArithmeticInstruction";
			InstructionFinder finder = new InstructionFinder(instList);
			Iterator iterator = finder.search(regex);
			while (iterator.hasNext()) {//...}
				InstructionHandle[] instructions = (InstructionHandle[]) iterator.next();
				int instNum = 0;

				Number Num1 = getNum(instNum, cpgen, instructions);
				instNum += 1;

				ConversionInstruction conversion1 = getConversion(instNum, instructions);
				if (conversion1 != null) {
					instNum += 1;
				}

				Number Num2 = getNum(instNum, cpgen, instructions);
				instNum += 1;

				ConversionInstruction conversion2 = getConversion(instNum, instructions);
				if (conversion2 != null) {
					instNum += 1;
				}

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

	private ConversionInstruction getConversion(int instNum, InstructionHandle[] instructions) {
		ConversionInstruction conversion = null;
		if (instructions[instNum].getInstruction() instanceof ConversionInstruction) {
			conversion = (ConversionInstruction) instructions[instNum].getInstruction();
		}
		return conversion;
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




	private void resolveBinaryComparisonForInt(ClassGen cgen, ConstantPoolGen cpgen, InstructionList il){
		System.out.println("*** Optimization: resolve binary comaprison ***");

		InstructionFinder finder = new InstructionFinder(il);
		Iterator itr = finder.search("PushInstruction PushInstruction IfInstruction");
		boolean performedOptimization = false;
		while (itr.hasNext()) {
			InstructionHandle[] match = (InstructionHandle[]) itr.next();

			// match[0] is a PushInstruction (expect an LDC)
			PushInstruction pushInstructionOne = (PushInstruction) match[0].getInstruction();
			Object valueA = null;
			
			if (pushInstructionOne instanceof LDC) {
				LDC constantLoadInstruction = (LDC) pushInstructionOne;
				valueA = constantLoadInstruction.getValue(cpgen);
			} else if (pushInstructionOne instanceof LDC2_W) {
				LDC2_W constantLoadInstruction = (LDC2_W) pushInstructionOne;
				valueA = constantLoadInstruction.getValue(cpgen);
			}
			

			Integer valueOne = (Integer) valueA;
			System.out.println("PushInstruction 1 value: " + valueOne);


			// match[1] is a PushInstruction (expect an LDC)
			PushInstruction pushInstructionTwo = (PushInstruction) match[1].getInstruction();
			Object valueB = null;
			if (pushInstructionTwo instanceof LDC) {
				LDC constantLoadInstruction = (LDC) pushInstructionTwo;
				valueB = constantLoadInstruction.getValue(cpgen);
			} else if (pushInstructionTwo instanceof LDC2_W) {
				LDC2_W constantLoadInstruction = (LDC2_W) pushInstructionTwo;
				valueB = constantLoadInstruction.getValue(cpgen);
			}

			
			Integer valueTwo = (Integer) valueB;
			System.out.println("PushInstruction 2 value: " + valueTwo);

			if (valueOne == null || valueTwo == null){
				continue;
			}

			// match[2] is an IfInstruction (expect an LDC)
			IfInstruction ifInstruction = (IfInstruction) (match[2].getInstruction());

			boolean branch = false;
			boolean canHandle = true;

			if (ifInstruction instanceof IF_ICMPEQ) {
				branch = valueOne.equals(valueTwo);
			} else if (ifInstruction instanceof IF_ICMPNE) {
				branch = !valueOne.equals(valueTwo);
			} else if (ifInstruction instanceof IF_ICMPLT) {
				branch = valueOne.compareTo(valueTwo) < 0;
			} else if (ifInstruction instanceof IF_ICMPLE) {
				branch = valueOne.compareTo(valueTwo) <= 0;
			} else if (ifInstruction instanceof IF_ICMPGT) {
				branch = valueOne.compareTo(valueTwo) > 0;
			} else if (ifInstruction instanceof IF_ICMPGE) {
				branch = valueOne.compareTo(valueTwo) >= 0;
			} else {
				canHandle = false;
			}
			
			if(canHandle) {
				performedOptimization = true;
					if (branch) {
						match[2].setInstruction(new GOTO(ifInstruction.getTarget()));
						il.redirectBranches(match[0], match[2]);
						System.out.println("Binary folding complete");
						try {
							il.delete(match[0], match[1]);
						} catch (TargetLostException e) {
						}
					} else {
						il.redirectBranches(match[0], match[2].getNext());
						try {
							il.delete(match[0], match[2]);
						} catch (TargetLostException e) {
					}
				}
			}
		}
	}

	private void resolveBinaryComparisonForLong(ClassGen cgen, ConstantPoolGen cpgen, InstructionList il){
		System.out.println("* * Optimization: resolve binary comaprison for long --------------");

		InstructionFinder finder = new InstructionFinder(il);
		Iterator itr = finder.search("PushInstruction PushInstruction LCMP IfInstruction");
		boolean performedOptimization = false;
		while (itr.hasNext()) {
			InstructionHandle[] match = (InstructionHandle[]) itr.next();

			// match[0] is a PushInstruction (expect an LDC)
			PushInstruction pushInstructionOne = (PushInstruction) match[0].getInstruction();
			Object valueA = null;
			Type typeA = null;

			if (pushInstructionOne instanceof LDC) {
				LDC constantLoadInstruction = (LDC) pushInstructionOne;
				valueA = constantLoadInstruction.getValue(cpgen);
				typeA = constantLoadInstruction.getType(cpgen);
			} else if (pushInstructionOne instanceof LDC2_W) {
				LDC2_W constantLoadInstruction = (LDC2_W) pushInstructionOne;
				valueA = constantLoadInstruction.getValue(cpgen);
				typeA = constantLoadInstruction.getType(cpgen);
			}

			if (typeA != Type.LONG) {
				break;
			}

			Long valueOne = (Long) valueA;

			System.out.println("PushInstruction 1 value: " + valueOne);


			// match[1] is a PushInstruction (expect an LDC)
			PushInstruction pushInstructionTwo = (PushInstruction) match[1].getInstruction();
			Object valueB = null;
			Type typeB = null;

			if (pushInstructionTwo instanceof LDC) {
				LDC constantLoadInstruction = (LDC) pushInstructionTwo;
				valueB = constantLoadInstruction.getValue(cpgen);
				typeB = constantLoadInstruction.getType(cpgen);
			} else if (pushInstructionTwo instanceof LDC2_W) {
				LDC2_W constantLoadInstruction = (LDC2_W) pushInstructionTwo;
				valueB = constantLoadInstruction.getValue(cpgen);
				typeB = constantLoadInstruction.getType(cpgen);
			}


			if (typeB != Type.LONG) {
				break;
			}

			Long valueTwo = (Long) valueB;
			System.out.println("PushInstruction 2 value: " + valueTwo);

			if (valueOne == null || valueTwo == null){
				continue;
			}

			// match[3] is an IfInstruction (expect an LDC)
			IfInstruction ifInstruction = (IfInstruction) (match[3].getInstruction());

			boolean branch = false;
			boolean canHandle = true;


			if (ifInstruction instanceof IF_ICMPEQ) {
				branch = valueOne.equals(valueTwo);
			} else if (ifInstruction instanceof IF_ICMPNE) {
				branch = !valueOne.equals(valueTwo);
			} else if (ifInstruction instanceof IF_ICMPLT) {
				branch = valueOne.compareTo(valueTwo) < 0;
			} else if (ifInstruction instanceof IF_ICMPLE) {
				branch = valueOne.compareTo(valueTwo) <= 0;
			} else if (ifInstruction instanceof IFLE ) {
				branch = valueOne.compareTo(valueTwo) <= 0;
			} else if (ifInstruction instanceof IF_ICMPGT) {
				branch = valueOne.compareTo(valueTwo) > 0;
			} else if (ifInstruction instanceof IF_ICMPGE) {
				branch = valueOne.compareTo(valueTwo) >= 0;
			} else {
				canHandle = false;
			}


			if(canHandle) {
				performedOptimization = true;
				if (branch) {
					match[3].setInstruction(new GOTO(ifInstruction.getTarget()));
					il.redirectBranches(match[0], match[3]);
					System.out.println("Binary folding for long complete");
					try {
						il.delete(match[0], match[1]);
					} catch (TargetLostException e) {
					}
				} else {
					// if branch is false
					il.redirectBranches(match[0], match[3].getNext());
					try {
						il.delete(match[0], match[1]);
						il.delete(match[2], match[3]);
					} catch (TargetLostException e) {
					}
				}
			}
		}
	}



	private void findConstantVariables(HashMap constantVariables, Iterator it){
		while(it.hasNext()){
			InstructionHandle[] match = (InstructionHandle[]) it.next();

			int localVarIndex = -1;

			// match StoreInstruction (only one pattern to match)
			localVarIndex = ((StoreInstruction) match[0].getInstruction()).getIndex();
			

			// check if localVarIndex is in dictionary
			if (!constantVariables.containsKey(localVarIndex)){
				constantVariables.put(localVarIndex,true);
			}
			else{
				//not a constant variable due to reassignment
				constantVariables.put(localVarIndex, false);
			}
		}
	}
	
	private void findLoadAndStoreInstructionPairs(HashMap constantVariables, HashMap literals, Iterator it, ConstantPoolGen cpgen){
		while(it.hasNext()){
			InstructionHandle[] match = (InstructionHandle[]) it.next();

			// match[0] is a PushInstruction
			PushInstruction pushInstruction = (PushInstruction) match[0].getInstruction();

			// match[1] is a StoreInstruction
			StoreInstruction storeInstruction = (StoreInstruction) match[1].getInstruction();

			// Check if this store instruction corresponds to a constant variable (in constant value dict)
			if (!constantVariables.containsKey(storeInstruction.getIndex())){
				// skip
				continue;
			}
			Number literalValue = null;

			
			if (pushInstruction instanceof ConstantPushInstruction) {
				literalValue = ((ConstantPushInstruction) pushInstruction).getValue();
			} else if (pushInstruction instanceof LDC) {
				literalValue = (Number) ((LDC) pushInstruction).getValue(cpgen);
			} else if (pushInstruction instanceof LDC2_W) {
				literalValue = ((LDC2_W) pushInstruction).getValue(cpgen);
			}


			// Store the literal value in the dictionary
			literals.put(storeInstruction.getIndex(), literalValue);
		}
		
	}

	private Instruction getNewPushInstruction(LoadInstruction loadInstruction, Number literalValue, ConstantPoolGen cpgen ){
		Instruction instructionAdded = null;
		int caseNum = -1;

		// find the type of load instruction to produce new push instruction
		if (loadInstruction.getType(cpgen) == Type.INT){
			caseNum = 1;
		}else if(loadInstruction.getType(cpgen) == Type.FLOAT){
			caseNum= 2;
		}else if(loadInstruction.getType(cpgen) == Type.DOUBLE) {
			caseNum = 3;
		}else if(loadInstruction.getType(cpgen) == Type.LONG){
			caseNum = 4;
		}

		switch(caseNum){
			case 1 :
				if (false && Math.abs(literalValue.intValue()) < Byte.MAX_VALUE) {
					instructionAdded = new BIPUSH(literalValue.byteValue());
				} else if (false && Math.abs(literalValue.intValue()) < Short.MAX_VALUE) {
					instructionAdded = new SIPUSH(literalValue.shortValue());
				} else {
					// Add to the constant pool.
					instructionAdded = new LDC(cpgen.addInteger(literalValue.intValue()));
				}
				break;

			case 2 :
				// Add to the constant pool.
				instructionAdded = new LDC(cpgen.addFloat(literalValue.floatValue()));
				break;

			case 3 :
				// Add to the constant pool.
				instructionAdded = new LDC2_W(cpgen.addDouble(literalValue.doubleValue()));
				break;

			case 4 :
				// Add to the constant pool.
				instructionAdded = new LDC2_W(cpgen.addLong(literalValue.longValue()));
				break;
		}
		return instructionAdded;
	}

	private void replaceLoadInstructionWithPushInstruction( HashMap constantVariables, HashMap literals, Iterator it, InstructionList il, ConstantPoolGen cpgen){
		while(it.hasNext()) {
			InstructionHandle[] match = (InstructionHandle[]) it.next();

			// match[0] is a LoadInstruction
			LoadInstruction loadInstruction = (LoadInstruction) match[0].getInstruction();


			// Check if the index exists in the dictionary.
			if (literals.containsKey(loadInstruction.getIndex())) {

				// Replace the LoadInstruction with the literal value.
				Number literalValue = (Number) literals.get(loadInstruction.getIndex());

				//get new push instruction to replace load instruction
				Instruction instructionAdded = getNewPushInstruction(loadInstruction, literalValue, cpgen);

				InstructionHandle instructionAddedHandle = il.insert(match[0], instructionAdded);

				try {
					// Delete old load instructons
					il.delete(match[0]);
				} catch (TargetLostException e) {
					for (InstructionHandle target : e.getTargets()) {
						for (InstructionTargeter targeter : target.getTargeters()) {
							targeter.updateTarget(target, instructionAddedHandle);
						}
					}
				}
			}
		}
	}

	public void constantVariableFolding(ClassGen cgen, ConstantPoolGen cpgen, Method method, InstructionList il) {
		
		// Higher view is to replace every load instruction with a push instruction from constant pool
		// repeat this process until no more load instructions:
		// 1. doSimpleFolding (fold arithmetic)(triple)
		// 2. Look for pairs of push and store instructions. (Store literals in dictionary, must match constantValue dict first)
		// 3. Replace load instruction with a push literal instruction from literal dict.

		System.out.println("*** Started constant variable folding ***");
		// dictionary for literal values
		HashMap<Integer, Number> literals = new HashMap<>();
		// dictionary for constant variables
		HashMap<Integer, Boolean> constantVariables = new HashMap<>();

		//initialsiation to find constant variables
		InstructionFinder finder = new InstructionFinder(il);
		String patternOne = "StoreInstruction";
		Iterator it = finder.search(patternOne);

		//First pass through instructionList to find all constant variables in instructionList
		//and store in dict (constantVariables).
		findConstantVariables(constantVariables, it);

		// The loop will end when there are no longer any LoadInstructions whose index exists in the literalValues hashmap.
		boolean foldedLoadInstruction;
		while(true) {

			// 1. Run simple folding to get as many literals as possible. Addtional optimisation on goto statements.
			simpleFolding(cgen, cpgen, method, il);
			System.out.println("***Done simple folding***");
			resolveBinaryComparisonForInt(cgen, cpgen, il);
			resolveBinaryComparisonForLong(cgen, cpgen, il);

			// 2. Store all literals in the hashmap.
			finder = new InstructionFinder(il);
			String patternTwo = "(LDC | LDC2_W | LDC_W | ConstantPushInstruction) (DSTORE | FSTORE | ISTORE | LSTORE)";
			it = finder.search(patternTwo);
			findLoadAndStoreInstructionPairs(constantVariables, literals, it, cpgen);


			//initialsiation to find load instructions variables
			finder = new InstructionFinder(il);
			String patternThree = "(DLOAD | FLOAD | ILOAD | LLOAD)";
			it = finder.search(patternThree);
			if(!it.hasNext()){
				break;
			}
			else{
				// 3. Look for LoadInstruction and check if the index exists in the hashmap.
				// If it does, replace the LoadInstruction with the literal value.
				replaceLoadInstructionWithPushInstruction(constantVariables, literals, it, il, cpgen);
			}

		}

		System.out.println("*** Completed constant variable folding ***");
	}


	private void replaceBipushAndSipush(ClassGen cgen, ConstantPoolGen cpgen, InstructionList il) {
		System.out.println("***Replacing BIPUSH and SIPUSH");

		InstructionFinder finder = new InstructionFinder(il);
		Iterator itr = finder.search("PushInstruction");
		InstructionHandle newInst = null;

		while (itr.hasNext()) {
			InstructionHandle[] match = (InstructionHandle[]) itr.next();
			PushInstruction pushInstruction = (PushInstruction) match[0].getInstruction();

			if (pushInstruction instanceof BIPUSH || pushInstruction instanceof SIPUSH) {
				System.out.println("Entered BIPUSH SIPUSH");
				Number literalValue = null;
				if(pushInstruction instanceof BIPUSH) {
					literalValue = (Number) ((BIPUSH) match[0].getInstruction()).getValue();
				}else{
					literalValue = (Number) ((SIPUSH) match[0].getInstruction()).getValue();
				}
				Instruction instructionAdded = new LDC(cpgen.addInteger(literalValue.intValue()));
				newInst = il.insert(match[0], instructionAdded );
				try {
					// Delete old load instructons
					il.delete(match[0]);
				} catch (TargetLostException e) {
					for (InstructionHandle target : e.getTargets()) {
						for (InstructionTargeter targeter : target.getTargeters()) {
							targeter.updateTarget(target, newInst);
						}
					}
				}
			}

		}
	}

	private void optimizeMethod(ClassGen cgen, ConstantPoolGen cpgen, Method method) {
		MethodGen mgen = new MethodGen(method, cgen.getClassName(), cpgen);
		InstructionList instList = mgen.getInstructionList();

		constantVariableFolding(cgen, cpgen, method, instList);
		replaceBipushAndSipush(cgen, cpgen, instList);
		System.out.println(cgen.getClassName() + " > " + method.getName());
		System.out.println(instList);
		System.out.println("");
		System.out.println("");


		mgen.setMaxStack();
		mgen.setMaxLocals();

		Method newM = mgen.getMethod();
		cgen.replaceMethod(method, newM);

	}

	
	public void optimize()
	{
		ClassGen cgen = new ClassGen(original);
		ConstantPoolGen cpgen = cgen.getConstantPool();

		// Implement your optimization here

		cgen.setMajor(50);

		Method[] methods = cgen.getMethods();
		for (Method method : methods) {
			optimizeMethod(cgen, cpgen, method);
		}


		this.optimized = cgen.getJavaClass();
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