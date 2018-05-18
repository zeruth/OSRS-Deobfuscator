/*
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
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
package deob;

import com.google.common.base.Stopwatch;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import asm.ClassGroup;
import asm.execution.Execution;
import deob.deobfuscators.transformers.GetPathTransformer;
import deob.deobfuscators.CastNull;
import deob.deobfuscators.constparam.ConstantParameter;
import deob.deobfuscators.EnumDeobfuscator;
import deob.deobfuscators.FieldInliner;
import deob.deobfuscators.IllegalStateExceptions;
import deob.deobfuscators.Lvt;
import deob.deobfuscators.Order;
import deob.deobfuscators.RenameUnique;
import deob.deobfuscators.RuntimeExceptions;
import deob.deobfuscators.UnreachedCode;
import deob.deobfuscators.UnusedClass;
import deob.deobfuscators.UnusedFields;
import deob.deobfuscators.UnusedMethods;
import deob.deobfuscators.UnusedParameters;
import deob.deobfuscators.arithmetic.ModArith;
import deob.deobfuscators.arithmetic.MultiplicationDeobfuscator;
import deob.deobfuscators.arithmetic.MultiplyOneDeobfuscator;
import deob.deobfuscators.arithmetic.MultiplyZeroDeobfuscator;
import deob.deobfuscators.cfg.ControlFlowDeobfuscator;
import deob.deobfuscators.exprargorder.ExprArgOrder;
import deob.deobfuscators.menuaction.MenuActionDeobfuscator;
import deob.deobfuscators.transformers.ClientErrorTransformer;
import deob.deobfuscators.transformers.MaxMemoryTransformer;
import deob.deobfuscators.transformers.OpcodesTransformer;
import deob.deobfuscators.transformers.ReflectionTransformer;
import deob.util.JarUtil;

public class Deob
{

	public static final int OBFUSCATED_NAME_MAX_LEN = 2;
	private static final boolean CHECK_EXEC = false;

	public static void main(String inputJar, String outputJar) throws IOException
	{

		Stopwatch stopwatch = Stopwatch.createStarted();

		ClassGroup group = JarUtil.loadJar(new File(inputJar));

		
		System.out.println("RuntimeExceptions");
		run(group, new RuntimeExceptions());

		System.out.println("ControlFlow");
		run(group, new ControlFlowDeobfuscator());

		System.out.println("RenameUnique");
		run(group, new RenameUnique());

		System.out.println("UnreachedCode");
		run(group, new UnreachedCode());
		
		System.out.println("UnusedMethods");
		run(group, new UnusedMethods());

		System.out.println("IllegalStateExceptions");
		run(group, new IllegalStateExceptions());

		System.out.println("ConstantParameter");
		run(group, new ConstantParameter());

		System.out.println("UnreachedCode");
		run(group, new UnreachedCode());
		
		System.out.println("UnusedMethods");
		run(group, new UnusedMethods());

		System.out.println("UnusedParameters");
		run(group, new UnusedParameters());

		System.out.println("UnusedFields");
		run(group, new UnusedFields());

		System.out.println("FieldInliner");
		run(group, new FieldInliner());

		System.out.println("Order");
		run(group, new Order());

		System.out.println("UnusedClass");
		run(group, new UnusedClass());

		System.out.println("runMath");
		runMath(group);

		System.out.println("ExprArgOrder");
		run(group, new ExprArgOrder());

		System.out.println("Lvt");
		run(group, new Lvt());

		System.out.println("CastNull");
		run(group, new CastNull());

		System.out.println("EnumDeobfuscator");
		run(group, new EnumDeobfuscator());

		System.out.println("OpcodesTransformer");
		new OpcodesTransformer().transform(group);

		System.out.println("MenuActionDeobfuscator");
		run(group, new MenuActionDeobfuscator());

		System.out.println("GetPathTransformer");
		new GetPathTransformer().transform(group);
		
		System.out.println("ClientErrorTransformer");
		new ClientErrorTransformer().transform(group);
		
		System.out.println("ReflectionTransformer");
		new ReflectionTransformer().transform(group);
		
		System.out.println("MaxMemoryTransformer");
		new MaxMemoryTransformer().transform(group);

		JarUtil.saveJar(group, new File(outputJar));

		System.out.println("---Deobfuscation took " + stopwatch.elapsed(TimeUnit.SECONDS) + " seconds---");
		stopwatch.stop();
	}

	public static boolean isObfuscated(String name)
	{
		return name.length() <= OBFUSCATED_NAME_MAX_LEN || name.startsWith("method") || name.startsWith("vmethod") || name.startsWith("field") || name.startsWith("class");
	}

	private static void runMath(ClassGroup group)
	{
		ModArith mod = new ModArith();
		mod.run(group);

		int last = -1, cur;
		while ((cur = mod.runOnce()) > 0)
		{
			new MultiplicationDeobfuscator().run(group);

			// do not remove 1 * field so that ModArith can detect
			// the change in guessDecreasesConstants()
			new MultiplyOneDeobfuscator(true).run(group);

			new MultiplyZeroDeobfuscator().run(group);

			if (last == cur)
			{
				break;
			}

			last = cur;
		}

		// now that modarith is done, remove field * 1
		new MultiplyOneDeobfuscator(false).run(group);

		mod.annotateEncryption();
	}

	private static void run(ClassGroup group, Deobfuscator deob)
	{
		Stopwatch stopwatch = Stopwatch.createStarted();
		deob.run(group);
		stopwatch.stop();

		// check code is still correct
		if (CHECK_EXEC)
		{
			Execution execution = new Execution(group);
			execution.populateInitialMethods();
			execution.run();
		}
	}
}
