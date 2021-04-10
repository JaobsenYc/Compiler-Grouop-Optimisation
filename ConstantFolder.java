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

	public static final String OP_ADD = "add";
	public static final String OP_SUB = "sub";
	public static final String OP_MUL = "mul";
	public static final String OP_DIV = "div";

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



	
	
	private void resolveComparison(ClassGen cgen, ConstantPoolGen cpgen, InstructionList il){
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

	public void constantVariableFolding(ClassGen cgen, ConstantPoolGen cpgen, InstructionList il) {
		
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
			doSimpleFolding(cgen, cpgen, il);
			System.out.println("***Done simple folding***");
			resolveComparison(cgen, cpgen, il);

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



	
	public void optimize()
	{
		ClassGen classGen = new ClassGen(original);
		ConstantPoolGen constPoolGen = classGen.getConstantPool();


		// prints bytecode instruction list
		Method[] methods = classGen.getMethods();
		for (Method method : methods) {
			MethodGen methodGen = new MethodGen(method, classGen.getClassName(), constPoolGen);
			InstructionList il = methodGen.getInstructionList();
			constantVariableFolding(classGen, constPoolGen, il);
			System.out.println(classGen.getClassName() + " > " + method.getName());
			System.out.println(il);
			System.out.println("");
			System.out.println("");
			
		}
        
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