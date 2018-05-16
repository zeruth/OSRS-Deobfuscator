/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package deob.deobfuscators.packetwrite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import asm.ClassFile;
import asm.ClassGroup;
import asm.Type;
import asm.attributes.code.Instruction;
import static asm.attributes.code.InstructionType.INVOKEVIRTUAL;
import asm.attributes.code.Instructions;
import asm.attributes.code.Label;
import asm.attributes.code.instruction.types.InvokeInstruction;
import asm.attributes.code.instruction.types.LVTInstruction;
import asm.attributes.code.instruction.types.ReturnInstruction;
import asm.attributes.code.instruction.types.SetFieldInstruction;
import asm.attributes.code.instructions.GetStatic;
import asm.attributes.code.instructions.Goto;
import asm.attributes.code.instructions.If;
import asm.attributes.code.instructions.If0;
import asm.attributes.code.instructions.IfEq;
import asm.attributes.code.instructions.InvokeVirtual;
import asm.execution.Execution;
import asm.execution.InstructionContext;
import asm.execution.StackContext;
import asm.pool.Method;
import asm.signature.Signature;
import deob.Deobfuscator;
import deob.c2s.RWOpcodeFinder;

public class PacketWriteDeobfuscator implements Deobfuscator
{

	private static final String RUNELITE_PACKET = "RUNELITE_PACKET";

	private final OpcodeReplacer opcodeReplacer = new OpcodeReplacer();

	private RWOpcodeFinder rw;
	private PacketWrite cur;
	private final Map<Instruction, PacketWrite> writes = new HashMap<>();

	public void visit(InstructionContext ctx)
	{
		if (isEnd(ctx))
		{
			end();
			return;
		}

		if (ctx.getInstruction().getType() != INVOKEVIRTUAL)
		{
			return;
		}

		InvokeInstruction ii = (InvokeInstruction) ctx.getInstruction();

		if (ii.getMethods().contains(rw.getWriteOpcode()))
		{
			end();
			PacketWrite write = start();
			write.putOpcode = ctx;
			return;
		}

		PacketWrite write = cur;
		if (write == null)
		{
			return;
		}

		if (!ii.getMethod().getClazz().getName().equals(rw.getWriteOpcode().getClassFile().getSuperName()))
		{
			return;
		}

		write.writes.add(ctx);
	}

	private PacketWrite start()
	{
		end();
		cur = new PacketWrite();
		return cur;
	}

	private void end()
	{
		if (cur != null)
		{
			if (!writes.containsKey(cur.putOpcode.getInstruction()))
			{
				writes.put(cur.putOpcode.getInstruction(), cur);
			}
			cur = null;
		}
	}

	private boolean isEnd(InstructionContext ctx)
	{
		// conditions where packet write ends:
		// any invoke that isn't to the packet buffer
		// any variable assignment
		// any field assignment
		// any conditional jump
		// any return

		Instruction i = ctx.getInstruction();

		if (i instanceof InvokeInstruction)
		{
			InvokeInstruction ii = (InvokeInstruction) i;
			Method method = ii.getMethod();

			if (!method.getClazz().equals(rw.getSecretBuffer().getPoolClass())
				&& !method.getClazz().equals(rw.getBuffer().getPoolClass()))
			{
				return true;
			}
		}

		if (i instanceof LVTInstruction)
		{
			LVTInstruction lvt = (LVTInstruction) i;
			if (lvt.store())
			{
				return true;
			}
		}

		if (i instanceof SetFieldInstruction)
		{
			return true;
		}

		if (i instanceof If || i instanceof If0)
		{
			return true;
		}

		if (i instanceof ReturnInstruction)
		{
			return true;
		}

		return false;
	}

	@Override
	public void run(ClassGroup group)
	{
		rw = new RWOpcodeFinder(group);
		rw.find();

		Execution e = new Execution(group);
		e.addExecutionVisitor(this::visit);
		e.populateInitialMethods();
		e.run();

		end();

		opcodeReplacer.run(group, writes.values());

		int count = 0;
		int writesCount = 0;

		for (PacketWrite write : writes.values())
		{
			if (write.writes.isEmpty())
			{
				continue;
			}

			insert(group, write);
			++count;
			writesCount += write.writes.size();
		}

	}

	private void insert(ClassGroup group, PacketWrite write)
	{
		Instructions instructions = write.putOpcode.getInstruction().getInstructions();
		List<Instruction> ins = instructions.getInstructions();

		InstructionContext firstWrite = write.writes.get(0);
		InstructionContext lastWrite = write.writes.get(write.writes.size() - 1);

		int idx = ins.indexOf(lastWrite.getInstruction());
		assert idx != -1;
		++idx; // past write

		Label afterWrites = instructions.createLabelFor(ins.get(idx));

		// pops arg, getfield
		InstructionContext beforeFirstWrite = firstWrite.getPops().get(1).getPushed();
		Label putOpcode = instructions.createLabelFor(beforeFirstWrite.getInstruction(), true);

		idx = ins.indexOf(beforeFirstWrite.getInstruction());
		assert idx != -1;
		--idx;

		asm.pool.Field field = new asm.pool.Field(
			new asm.pool.Class(findClient(group).getName()),
			RUNELITE_PACKET,
			Type.BOOLEAN
		);

		instructions.addInstruction(idx++, new GetStatic(instructions, field));
		instructions.addInstruction(idx++, new IfEq(instructions, putOpcode));
		Instruction before = ins.get(idx);
		for (InstructionContext ctx : write.writes)
		{
			insert(instructions, ctx, before);
		}
		idx = ins.indexOf(before);
		instructions.addInstruction(idx++, new Goto(instructions, afterWrites));
	}

	private void insert(Instructions ins, InstructionContext ic, Instruction before)
	{
		List<StackContext> pops = new ArrayList<>(ic.getPops());
		Collections.reverse(pops);
		for (StackContext sc : pops)
		{
			insert(ins, sc.getPushed(), before);
		}

		Instruction i = ic.getInstruction().clone();
		i = translate(i);
		assert i.getInstructions() == ins;

		int idx = ins.getInstructions().indexOf(before);
		assert idx != -1;

		ins.addInstruction(idx, i);
	}

	private Instruction translate(Instruction i)
	{
		if (!(i instanceof InvokeVirtual))
		{
			return i;
		}

		InvokeVirtual ii = (InvokeVirtual) i;
		Method invoked = ii.getMethod();

		assert invoked.getType().size() == 1;

		Type argumentType = invoked.getType().getTypeOfArg(0);

		Method invokeMethod;
		if (argumentType.equals(Type.BYTE))
		{
			invokeMethod = new Method(
				ii.getMethod().getClazz(),
				"runeliteWriteByte",
				new Signature("(B)V")
			);
		}
		else if (argumentType.equals(Type.SHORT))
		{
			invokeMethod = new Method(
				ii.getMethod().getClazz(),
				"runeliteWriteShort",
				new Signature("(S)V")
			);
		}
		else if (argumentType.equals(Type.INT))
		{
			invokeMethod = new Method(
				ii.getMethod().getClazz(),
				"runeliteWriteInt",
				new Signature("(I)V")
			);
		}
		else if (argumentType.equals(Type.LONG))
		{
			invokeMethod = new Method(
				ii.getMethod().getClazz(),
				"runeliteWriteLong",
				new Signature("(J)V")
			);
		}
		else if (argumentType.equals(Type.STRING))
		{
			invokeMethod = new Method(
				ii.getMethod().getClazz(),
				"runeliteWriteString",
				new Signature("(Ljava/lang/String;)V")
			);
		}
		else
		{
			throw new IllegalStateException("Unknown type " + argumentType);
		}

		return new InvokeVirtual(i.getInstructions(), invokeMethod);
	}

	private ClassFile findClient(ClassGroup group)
	{
		// "client" in vainlla but "Client" in deob..
		ClassFile cf = group.findClass("client");
		return cf != null ? cf : group.findClass("Client");
	}
}
