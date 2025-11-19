import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class Sistema {

	// --------------------- Logger para escrita em arquivo ----------------------

	public static class SystemLogger {
		private static final String LOG_FILE = "sistema_log.txt";
		private static PrintWriter writer;

		public static void initialize() {
			try {
				// Usa FileWriter com 'false' para sobrescrever, e depois o writer
				writer = new PrintWriter(new FileWriter(LOG_FILE, false));
				System.out.println("Log de transições inicializado em: " + LOG_FILE);
				logHeader();
			} catch (IOException e) {
				System.err.println("Erro ao inicializar o arquivo de log: " + e.getMessage());
			}
		}

		private static void logHeader() {
			if (writer != null) {
				writer.println("PID | NOME DO PROG | RAZÃO DA MUDANÇA | ESTADO INICIAL | PROX. ESTADO | TABELA DE PÁGINAS");
				writer.flush();
			}
		}

		public static void log(String entry) {
			if (writer != null) {
				writer.println(entry);
				writer.flush();
			}
		}

		public static void close() {
			if (writer != null) {
				writer.close();
			}
		}
	}


	// --------------------- H A R D W A R E - definicoes de HW --------------------

	public class Memory {
		public Word[] pos;

		public Memory(int size) {
			pos = new Word[size];
			for (int i = 0; i < pos.length; i++) {
				pos[i] = new Word(Opcode.___, -1, -1, -1);
			}
			;
		}
	}

	public class Word {
		public Opcode opc;
		public int ra;
		public int rb;
		public int p;

		public Word(Opcode _opc, int _ra, int _rb, int _p) {
			opc = _opc;
			ra = _ra;
			rb = _rb;
			p  = _p;
		}
	}

	public enum Opcode {
		DATA, ___,
		JMP, JMPI, JMPIG, JMPIL, JMPIE,
		JMPIM, JMPIGM, JMPILM, JMPIEM,
		JMPIGK, JMPILK, JMPIEK, JMPIGT,
		ADDI, SUBI, ADD, SUB, MULT,
		LDI, LDD, STD, LDX, STX, MOVE,
		SYSCALL, STOP
	}

	public enum Interrupts {
		noInterrupt, intEnderecoInvalido, intInstrucaoInvalida, intOverflow,
		intClock,
		intStop,
		intIO,
		intPageFault
	}

	public class CPU {
		private int maxInt;
		private int minInt;

		private int pc;
		private Word ir;
		private int[] reg;
		private Interrupts irpt;
		private int timeSlice = 0;
		private int ticks = 0;
		private int faultingLogicalAddress;


		private Word[] m;

		private PageTableEntry[] pageTable;
		private int tamPag;


		private InterruptHandling ih;
		private SysCallHandling sysCall;

		private boolean cpuStop;



		private boolean debug;
		private Utilities u;

		public CPU(Memory _mem, boolean _debug) {
			maxInt = 32767;
			minInt = -32767;
			m = _mem.pos;
			reg = new int[10];

			debug = _debug;

		}

		public int getFaultingLogicalAddress() {
			return faultingLogicalAddress;
		}

		public void setTimeSlice(int delta) {
			this.timeSlice = Math.max(0, delta);
		}

		public void setAddressOfHandlers(InterruptHandling _ih, SysCallHandling _sysCall) {
			ih = _ih;
			sysCall = _sysCall;
		}

		public void setUtilities(Utilities _u) {
			u = _u;
		}

		public void setDebug(boolean on) {
			this.debug = on;
		}


		private boolean legal(int e) {
			return phys(e) >= 0;
		}

		private boolean testOverflow(int v) {
			if ((v < minInt) || (v > maxInt)) {
				irpt = Interrupts.intOverflow;
				return false;
			}
			;
			return true;
		}
		public int getPC() { return pc; }

		public void copyRegsTo(int[] out) {
			for (int i = 0; i < 10; i++) out[i] = reg[i];
		}


		public void setContext(int _pc, int[] regs) {
			pc = _pc;
			for (int i = 0; i < 10; i++) reg[i] = (regs != null ? regs[i] : 0);
			irpt = Interrupts.noInterrupt;
		}

		public void setContext(int _pc) {

			pc = _pc;
			irpt = Interrupts.noInterrupt;
		}


		public void setMMU(PageTableEntry[] _pageTable, int _tamPag) {
			this.pageTable = _pageTable;
			this.tamPag = _tamPag;
		}

		private int phys(int logical) {
			if (logical < 0 || pageTable == null || tamPag <= 0) {
				irpt = Interrupts.intEnderecoInvalido;
				return -1;
			}
			int page = logical / tamPag;
			int off  = logical % tamPag;
			if (page < 0 || page >= pageTable.length) {
				irpt = Interrupts.intEnderecoInvalido;
				return -1;
			}

			PageTableEntry entry = pageTable[page];
			if (!entry.isPresent) {
				this.faultingLogicalAddress = logical;
				irpt = Interrupts.intPageFault;
				return -1;
			}

			int frame = entry.frameNum;
			int phys  = frame * tamPag + off;
			if (phys < 0 || phys >= m.length) {
				irpt = Interrupts.intEnderecoInvalido;
				return -1;
			}
			return phys;
		}

		public void run() {

			ticks = timeSlice;
			cpuStop = false;
			while (!cpuStop) {


				int pcFisico = phys(pc);
				if (irpt != Interrupts.noInterrupt) {

				} else if (pcFisico >= 0) {
					ir = m[pcFisico];


					if (debug) {
						System.out.print("                                              regs: ");
						for (int i = 0; i < 10; i++) {
							System.out.print(" r[" + i + "]:" + reg[i]);
						}
						;
						System.out.println();
					}
					if (debug) {
						System.out.print("                      pc: " + pc + "       exec: ");
						u.dump(ir);
					}


					switch (ir.opc) {


						case LDI:
							reg[ir.ra] = ir.p;
							pc++;
							break;
						case LDD:
							int pFisicoLDD = phys(ir.p);
							if (irpt == Interrupts.noInterrupt) {
								reg[ir.ra] = m[pFisicoLDD].p;
								pc++;
							}
							break;
						case LDX:
							int pFisicoLDX = phys(reg[ir.rb]);
							if (irpt == Interrupts.noInterrupt) {
								reg[ir.ra] = m[pFisicoLDX].p;
								pc++;
							}
							break;
						case STD:
							int pFisicoSTD = phys(ir.p);
							if (irpt == Interrupts.noInterrupt) {
								int __a = pFisicoSTD;

								pageTable[ir.p / tamPag].isDirty = true;

								m[__a].opc = Opcode.DATA;
								m[__a].p = reg[ir.ra];
								pc++;
								if (debug) {
									System.out.print("                                  ");
									u.dump(__a, __a + 1);
								}
							}
							break;
						case STX:
							int pFisicoSTX = phys(reg[ir.ra]);
							if (irpt == Interrupts.noInterrupt) {
								int __a = pFisicoSTX;

								pageTable[reg[ir.ra] / tamPag].isDirty = true;

								m[__a].opc = Opcode.DATA;
								m[__a].p = reg[ir.rb];
								pc++;
							}
							break;
						case MOVE:
							reg[ir.ra] = reg[ir.rb];
							pc++;
							break;

						case ADD:
							reg[ir.ra] = reg[ir.ra] + reg[ir.rb];
							testOverflow(reg[ir.ra]);
							pc++;
							break;
						case ADDI:
							reg[ir.ra] = reg[ir.ra] + ir.p;
							testOverflow(reg[ir.ra]);
							pc++;
							break;
						case SUB:
							reg[ir.ra] = reg[ir.ra] - reg[ir.rb];
							testOverflow(reg[ir.ra]);
							pc++;
							break;
						case SUBI:
							reg[ir.ra] = reg[ir.ra] - ir.p;
							testOverflow(reg[ir.ra]);
							pc++;
							break;
						case MULT:
							reg[ir.ra] = reg[ir.ra] * reg[ir.rb];
							testOverflow(reg[ir.ra]);
							pc++;
							break;


						case JMP:
							pc = ir.p;
							break;
						case JMPIM:
							int pFisicoJMPIM = phys(ir.p);
							if (irpt == Interrupts.noInterrupt) {
								pc = m[pFisicoJMPIM].p;
							}
							break;
						case JMPIG:
							if (reg[ir.rb] > 0) {
								pc = reg[ir.ra];
							} else {
								pc++;
							}
							break;
						case JMPIGK:
							if (reg[ir.rb] > 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;
						case JMPILK:
							if (reg[ir.rb] < 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;
						case JMPIEK:
							if (reg[ir.rb] == 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;
						case JMPIL:
							if (reg[ir.rb] < 0) {
								pc = reg[ir.ra];
							} else {
								pc++;
							}
							break;
						case JMPIE:
							if (reg[ir.rb] == 0) {
								pc = reg[ir.ra];
							} else {
								pc++;
							}
							break;
						case JMPIGM:
							int pFisicoJMPIGM = phys(ir.p);
							if (irpt == Interrupts.noInterrupt) {
								if (reg[ir.rb] > 0) { pc = m[pFisicoJMPIGM].p; } else { pc++; }
							}
							break;
						case JMPILM:
							int pFisicoJMPILM = phys(ir.p);
							if (irpt == Interrupts.noInterrupt) {
								if (reg[ir.rb] < 0) { pc = m[pFisicoJMPILM].p; } else { pc++; }
							}
							break;
						case JMPIEM:
							int pFisicoJMPIEM = phys(ir.p);
							if (irpt == Interrupts.noInterrupt) {
								if (reg[ir.rb] == 0) { pc = m[pFisicoJMPIEM].p; } else { pc++; }
							}
							break;
						case JMPIGT:
							if (reg[ir.ra] > reg[ir.rb]) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;

						case DATA:
							irpt = Interrupts.intInstrucaoInvalida;
							break;


						case SYSCALL:
							sysCall.handle();

							pc++;
							break;

						case STOP:
							sysCall.stop();
							irpt = Interrupts.intStop;

							break;


						default:
							irpt = Interrupts.intInstrucaoInvalida;
							break;
					}

					if (timeSlice > 0) {
						ticks--;
						if (ticks <= 0 && irpt == Interrupts.noInterrupt) {
							irpt = Interrupts.intClock;
						}
					}
				}

				if (irpt != Interrupts.noInterrupt) {
					ih.handle(irpt);
					cpuStop = true;
				}
			}
		}
	}


	public class HW {
		public Memory mem;
		public CPU cpu;

		public HW(int tamMem) {
			mem = new Memory(tamMem);
			cpu = new CPU(mem, true);
		}
	}


	private interface GM {

		int allocateFrame(int pid, int pageNum);
		int findAndAllocateFreeFrame(int pid, int pageNum);
		void freeFrames(int pid, PageTableEntry[] tabelaPaginas);
		void freeFrame(int frameNum);
	}


	private class FrameInfo {
		int pid = -1;
		int pageNum = -1;
		boolean isFree = true;
	}

	private class GerenteMemoriaPaginado implements GM {
		private final int tamPg;
		private final int nFrames;
		private final FrameInfo[] frameTable;
		private int nextVictim = 0;

		GerenteMemoriaPaginado(int tamMem, int tamPg) {
			this.tamPg = tamPg;
			this.nFrames = tamMem / tamPg;
			this.frameTable = new FrameInfo[nFrames];
			for (int i = 0; i < nFrames; i++) {
				frameTable[i] = new FrameInfo();
			}
		}


		@Override
		public synchronized int allocateFrame(int pid, int pageNum) {

			int freeFrame = findAndAllocateFreeFrame(pid, pageNum);
			if (freeFrame != -1) {
				return freeFrame;
			}


			int victimFrame = nextVictim;
			nextVictim = (nextVictim + 1) % nFrames;

			FrameInfo victimInfo = frameTable[victimFrame];
			PCB victimPcb = so.gp.getPCB(victimInfo.pid);

			System.out.println("GM: Vitimando frame " + victimFrame + " (ocupado por pid " + victimInfo.pid + ", pag " + victimInfo.pageNum + ")");


			if (victimPcb != null && victimInfo.pageNum < victimPcb.tabelaPaginas.length) {
				PageTableEntry pteVitima = victimPcb.tabelaPaginas[victimInfo.pageNum];
				pteVitima.isPresent = false;
				pteVitima.frameNum = -1;


				if (pteVitima.isDirty) {
					System.out.println("GM: Página da vítima (pid " + victimPcb.id + ", pag " + victimInfo.pageNum + ") está suja. Salvando...");



					so.vmDevice.enqueue(new PageIORequest(victimPcb.id, victimInfo.pageNum, victimFrame,
							PageIORequest.Operation.WRITE_TO_SWAP, victimPcb.nome));
					pteVitima.isDirty = false;
					pteVitima.diskBlockNum = -99;
				}
			}


			frameTable[victimFrame].pid = pid;
			frameTable[victimFrame].pageNum = pageNum;
			frameTable[victimFrame].isFree = false;

			return victimFrame;
		}


		@Override
		public synchronized int findAndAllocateFreeFrame(int pid, int pageNum) {
			for (int f = 0; f < nFrames; f++) {
				if (frameTable[f].isFree) {
					frameTable[f].isFree = false;
					frameTable[f].pid = pid;
					frameTable[f].pageNum = pageNum;
					return f;
				}
			}
			return -1;
		}


		@Override
		public synchronized void freeFrame(int frameNum) {
			if (frameNum >= 0 && frameNum < nFrames) {
				frameTable[frameNum].isFree = true;
				frameTable[frameNum].pid = -1;
				frameTable[frameNum].pageNum = -1;
				// CONTEÚDO DA MEMÓRIA PRESERVADO, APENAS O METADADO É LIBERADO
			}
		}


		@Override
		public synchronized void freeFrames(int pid, PageTableEntry[] tabelaPaginas) {
			if (tabelaPaginas == null) return;
			for (PageTableEntry pte : tabelaPaginas) {
				if (pte.isPresent) {
					freeFrame(pte.frameNum);
				}
			}
		}
	}






	public class InterruptHandling {
		private HW hw;
		public volatile Interrupts lastIrpt = Interrupts.noInterrupt;

		public InterruptHandling(HW _hw) {
			hw = _hw;
		}

		public void handle(Interrupts irpt) {
			lastIrpt = irpt;
			switch (irpt) {
				case intClock:
					System.out.println("                                               Interrupcao FIM-DE-QUANTUM");
					break;
				case intIO:
					System.out.println("                                               Interrupcao IO (dispositivo)");
					break;
				case intStop:
					System.out.println("                                               Interrupcao STOP (fim de processo)");
					break;
				case intPageFault:
					System.out.println("                                               Interrupcao PAGE FAULT");
					so.gp.handlePageFault();
					lastIrpt = Interrupts.intClock;
					break;
				default:

					System.out.println("                                               Interrupcao " + irpt + "   pc: " + hw.cpu.pc);
			}
		}


	}


	public class SysCallHandling {
		private HW hw;

		public SysCallHandling(HW _hw) {
			hw = _hw;
		}

		public void stop() {

			System.out.println("                                               SYSCALL STOP");
		}

		public void handle() {



			PCB atual = so.gp.getRunning();
			if (atual == null) {
				System.out.println("  SYSCALL sem processo corrente.");
				return;
			}

			int addrLogico = hw.cpu.reg[9];
			int addrFisico = hw.cpu.phys(addrLogico);


			if (hw.cpu.irpt == Interrupts.intPageFault) {
				System.out.println("  SYSCALL: Page Fault ao acessar endereço de I/O " + addrLogico);


				return;
			} else if (hw.cpu.irpt != Interrupts.noInterrupt) {
				System.out.println("  SYSCALL: Endereço de I/O inválido " + addrLogico);

				return;
			}

			int oper = hw.cpu.reg[8];

			int addr = addrFisico;

			System.out.println("SYSCALL pars: " + oper + " / " + addrLogico + " (fisico: " + addr + ")");

			if (oper == 1 || oper == 2) {



				so.gp.bloqueia(atual);
				IORequest req = new IORequest(atual.id, oper, addr);
				io.enqueue(req);


				hw.cpu.irpt = Interrupts.intClock;
			} else {
				System.out.println("  PARAMETRO INVALIDO");
			}
		}

	}





	public class Utilities {
		private HW hw;
		private GM gm;

		public GM getGM() { return gm; }

		public Utilities(HW _hw) {
			hw = _hw;
			gm = new GerenteMemoriaPaginado(hw.mem.pos.length, TAM_PG);
		}

		public void loadSinglePage(Word[] progImage, int pageNum, int frameNum) {
			Word[] m = hw.mem.pos;

			int inicioProg = pageNum * TAM_PG;
			int inicioFrame = frameNum * TAM_PG;

			for (int i = 0; i < TAM_PG; i++) {
				int idxProg = inicioProg + i;
				int idxFrame = inicioFrame + i;

				if (idxFrame >= m.length) break;

				if (idxProg < progImage.length) {

					m[idxFrame].opc = progImage[idxProg].opc;
					m[idxFrame].ra  = progImage[idxProg].ra;
					m[idxFrame].rb  = progImage[idxProg].rb;
					m[idxFrame].p   = progImage[idxProg].p;
				} else {

					m[idxFrame].opc = Opcode.___;
					m[idxFrame].ra  = -1;
					m[idxFrame].rb  = -1;
					m[idxFrame].p   = -1;
				}
			}
		}



		public void dump(Word w) {
			System.out.print("[ ");
			System.out.print(w.opc);
			System.out.print(", ");
			System.out.print(w.ra);
			System.out.print(", ");
			System.out.print(w.rb);
			System.out.print(", ");
			System.out.print(w.p);
			System.out.println("  ] ");
		}

		public void dump(int ini, int fim) {
			Word[] m = hw.mem.pos;
			for (int i = ini; i < fim; i++) {
				System.out.print(i);
				System.out.print(":  ");
				dump(m[i]);
			}
		}

		public void dumpLogical(PageTableEntry[] tabelaPaginas, int tamProg) {
			Word[] m = hw.mem.pos;
			for (int i = 0; i < tamProg; i++) {
				int page = i / TAM_PG;
				int off  = i % TAM_PG;

				PageTableEntry pte = (page < tabelaPaginas.length) ? tabelaPaginas[page] : null;

				if (pte != null && pte.isPresent) {
					int phys = pte.frameNum * TAM_PG + off;
					System.out.print(i);
					System.out.print(":  ");
					dump(m[phys]);
				} else {
					System.out.print(i);
					System.out.println(":  [ (not present) ]");
				}
			}
		}

	}

	public class SO {
		public InterruptHandling ih;
		public SysCallHandling sc;
		public Utilities utils;
		public GP gp;
		public VMDevice vmDevice;
		public Programs progs;

		public SO(HW hw, Programs progs) {
			this.progs = progs;
			ih = new InterruptHandling(hw);
			sc = new SysCallHandling(hw);
			hw.cpu.setAddressOfHandlers(ih, sc);
			utils = new Utilities(hw);
			gp = new GP(hw, this);
			vmDevice = new VMDevice(hw, gp, progs, utils);
		}
	}
	public enum EstadoProc { READY, RUNNING, BLOCKED, TERMINATED, }


	public class PageTableEntry {
		public int frameNum = -1;
		public boolean isPresent = false;
		public boolean isDirty = false;
		public int diskBlockNum = -1;

	}

	public class PCB {
		public final int id;
		public final String nome;
		public final PageTableEntry[] tabelaPaginas;
		public final int tamPag;
		public final int tamLogico;
		public int pc;
		public EstadoProc estado;
		public final int[] regs = new int[10];

		public PCB(int id, String nome, int tamLogico, int tamPag) {
			this.id = id;
			this.nome = nome;
			this.tamLogico = tamLogico;
			this.tamPag = tamPag;
			this.pc = 0;
			this.estado = EstadoProc.READY;


			int numPages = (tamLogico + tamPag - 1) / tamPag;
			this.tabelaPaginas = new PageTableEntry[numPages];
			for (int i = 0; i < numPages; i++) {
				this.tabelaPaginas[i] = new PageTableEntry();

				this.tabelaPaginas[i].diskBlockNum = i;
			}
		}

		// Helper para o log de transição
		public String getPageTableLog() {
			StringBuilder sb = new StringBuilder("{ ");
			for (int i = 0; i < tabelaPaginas.length; i++) {
				PageTableEntry pte = tabelaPaginas[i];
				String localizacao;
				String frameInfo = "_";

				if (pte.isPresent) {
					localizacao = "mp";
					frameInfo = String.valueOf(pte.frameNum);
				} else if (pte.diskBlockNum != -1) {
					localizacao = "ms";
				} else {
					localizacao = "nulo";
				}

				String dirtyInfo = pte.isDirty ? "D" : "";

				// Formato: [pag, frame/_, onde esta]
				sb.append(String.format("[%d,%s,%s%s]", i, frameInfo, localizacao, dirtyInfo));
				if (i < tabelaPaginas.length - 1) {
					sb.append(", ");
				}
			}
			sb.append(" }");
			return sb.toString();
		}
	}


	public class GP {
		private Thread schedThread;
		private volatile boolean schedOn = false;
		private int defaultQuantum = 5;
		private final HW hw;
		private final Utilities utils;
		private final GM gm;
		private final SO so;

		private java.util.Map<Integer, PCB> tabela = new java.util.LinkedHashMap<>();
		private java.util.Queue<PCB> ready = new java.util.ArrayDeque<>();
		private java.util.Queue<PCB> blocked = new java.util.ArrayDeque<>();
		private java.util.Queue<PCB> blockedForVM = new java.util.ArrayDeque<>();
		private PCB running = null;
		private int nextPid = 1;

		public GP(HW hw, SO so) {
			this.hw = hw;
			this.so = so;
			this.utils = so.utils;
			this.gm = so.utils.getGM();
		}

		public PCB getPCB(int pid) { return tabela.get(pid); }

		private void logTransition(PCB pcb, String razao, EstadoProc estadoInicial, EstadoProc proximoEstado) {
			String inicio = (estadoInicial == null) ? "nulo" : estadoInicial.toString().toLowerCase();
			String proximo = (proximoEstado == null) ? "nulo" : proximoEstado.toString().toLowerCase();
			String logEntry = String.format("%-4d | %-12s | %-16s | %-16s | %-12s | %s",
					pcb.id,
					pcb.nome,
					razao,
					inicio,
					proximo,
					pcb.getPageTableLog());
			SystemLogger.log(logEntry);
		}

		private void saveContext(PCB pcb) {
			pcb.pc = hw.cpu.getPC();
			hw.cpu.copyRegsTo(pcb.regs);
		}

		private void restoreContext(PCB pcb) {
			hw.cpu.setMMU(pcb.tabelaPaginas, pcb.tamPag);
			hw.cpu.setContext(pcb.pc, pcb.regs);
		}

		public PCB getRunning() {
			return running;
		}

		public void bloqueia(PCB pcb) {
			if (pcb == null) return;
			EstadoProc estadoAnterior = pcb.estado;
			pcb.estado = EstadoProc.BLOCKED;
			running = null;
			blocked.add(pcb);
			logTransition(pcb, "syscall IO", estadoAnterior, EstadoProc.BLOCKED);
		}

		public void ioCompleted(int pid) {
			PCB pcb = tabela.get(pid);
			if (pcb == null) {
				return;
			}

			blocked.remove(pcb);
			if (pcb.estado != EstadoProc.TERMINATED) {
				logTransition(pcb, "fim IO", EstadoProc.BLOCKED, EstadoProc.READY);
				pcb.estado = EstadoProc.READY;
				ready.add(pcb);
			}
		}


		public void handlePageFault() {
			PCB pcb = getRunning();
			if (pcb == null) return;

			int logicalAddr = hw.cpu.getFaultingLogicalAddress();
			int pageNum = logicalAddr / pcb.tamPag;

			if (pageNum < 0 || pageNum >= pcb.tabelaPaginas.length) {


				so.ih.lastIrpt = Interrupts.intEnderecoInvalido;
				return;
			}

			System.out.println("GP: Tratando Page Fault: pid " + pcb.id + " (acessando pág " + pageNum + ")");


			int frameNum = gm.allocateFrame(pcb.id, pageNum);


			EstadoProc estadoAnterior = pcb.estado;
			pcb.estado = EstadoProc.BLOCKED;
			running = null;
			blockedForVM.add(pcb);
			logTransition(pcb, "pg fault", estadoAnterior, EstadoProc.BLOCKED);



			so.vmDevice.enqueue(new PageIORequest(pcb.id, pageNum, frameNum,
					PageIORequest.Operation.READ_FROM_PROGRAM, pcb.nome));


		}


		public void vmOperationCompleted(int pid, int pageNum, int frameNum) {
			PCB pcb = tabela.get(pid);
			if (pcb == null) {
				gm.freeFrame(frameNum);
				return;
			}


			if (pageNum < pcb.tabelaPaginas.length) {
				pcb.tabelaPaginas[pageNum].isPresent = true;
				pcb.tabelaPaginas[pageNum].frameNum = frameNum;
				pcb.tabelaPaginas[pageNum].isDirty = false;
			}


			if (blockedForVM.remove(pcb)) {
				if (pcb.estado != EstadoProc.TERMINATED) {
					logTransition(pcb, "fim pg fault", EstadoProc.BLOCKED, EstadoProc.READY);
					pcb.estado = EstadoProc.READY;
					ready.add(pcb);
					System.out.println("GP: Page Fault (pid " + pid + ", pág " + pageNum + ") resolvido. Processo pronto.");
				}
			}
		}


		public synchronized void schedOn(int quantum) {
			if (schedOn) return;
			if (quantum > 0) defaultQuantum = quantum;
			schedOn = true;
			schedThread = new Thread(() -> {
				while (schedOn) {
					PCB pcb = ready.poll();
					if (pcb == null) {
						try { Thread.sleep(10); } catch (InterruptedException ignored) {}
						continue;
					}
					if (pcb.estado != EstadoProc.TERMINATED) {
						stepRoundRobin(pcb, defaultQuantum);
					}
				}
			}, "Scheduler");
			schedThread.start();
			System.out.println("Scheduler ON (quantum=" + defaultQuantum + ").");
		}

		public synchronized void schedOff() {
			schedOn = false;
			if (schedThread != null) schedThread.interrupt();
			System.out.println("Scheduler OFF.");
		}
		private void stepRoundRobin(PCB pcb, int quantum) {

			logTransition(pcb, "escalona", EstadoProc.READY, EstadoProc.RUNNING);

			running = pcb;
			pcb.estado = EstadoProc.RUNNING;

			restoreContext(pcb);
			hw.cpu.setTimeSlice(quantum);
			so.ih.lastIrpt = Interrupts.noInterrupt;

			System.out.println("---------------------------------- inicia execucao (pid " + pcb.id + ")");
			hw.cpu.run();
			System.out.println("---------------------------------- fim execucao (pid " + pcb.id + ")");

			Interrupts motivo = so.ih.lastIrpt;
			if (motivo == Interrupts.intClock) {
				saveContext(pcb);
				running = null;
				if (pcb.estado == EstadoProc.BLOCKED) {

				} else {
					logTransition(pcb, "fatia tempo", EstadoProc.RUNNING, EstadoProc.READY);
					pcb.estado = EstadoProc.READY;
					ready.add(pcb);
				}
			} else if (motivo == Interrupts.intIO) {
				saveContext(pcb);
				logTransition(pcb, "int. IO", EstadoProc.RUNNING, EstadoProc.READY);
				pcb.estado = EstadoProc.READY;
				running = null;
				ready.add(pcb);
			} else {
				if (motivo == Interrupts.intPageFault) {



				} else {
					String razao = (motivo == Interrupts.intStop) ? "termino (stop)" : "erro: " + motivo.name();
					logTransition(pcb, razao, EstadoProc.RUNNING, EstadoProc.TERMINATED);
					pcb.estado = EstadoProc.TERMINATED;
					running = null;
					gm.freeFrames(pcb.id, pcb.tabelaPaginas);
					tabela.remove(pcb.id);
					System.out.println("Processo " + pcb.id + " finalizado. Motivo: " + motivo);
				}
			}
		}

		public void execAll(int quantum) {
			if (quantum <= 0) quantum = 5;
			while (!ready.isEmpty()) {
				PCB pcb = ready.poll();
				if (pcb == null) break;
				if (pcb.estado == EstadoProc.TERMINATED) continue;
				stepRoundRobin(pcb, quantum);
			}
		}



		public Integer criaProcesso(String progName, Programs programs) {
			Word[] img = programs.retrieveProgram(progName);
			if (img == null) return null;


			int tamLogico = img.length;


			if ("PC".equals(progName) && tamLogico < 100) {
				tamLogico = 100;
			}

			int pid = nextPid++;
			PCB pcb = new PCB(pid, progName, tamLogico, TAM_PG);



			int frame0 = gm.findAndAllocateFreeFrame(pid, 0);
			if (frame0 == -1) {

				System.out.println("GM: Sem frame livre para pág 0. Vitimando...");
				frame0 = gm.allocateFrame(pid, 0);
			}


			utils.loadSinglePage(img, 0, frame0);


			pcb.tabelaPaginas[0].frameNum = frame0;
			pcb.tabelaPaginas[0].isPresent = true;

			tabela.put(pid, pcb);
			logTransition(pcb, "criacao", null, EstadoProc.READY);
			ready.add(pcb);
			return pid;
		}


		public boolean desalocaProcesso(int pid) {
			PCB pcb = tabela.get(pid);
			if (pcb == null) return false;

			ready.remove(pcb);
			blocked.remove(pcb);
			blockedForVM.remove(pcb);

			logTransition(pcb, "desaloca", pcb.estado, EstadoProc.TERMINATED);


			if (running == pcb) {
				running = null;
			}

			gm.freeFrames(pcb.id, pcb.tabelaPaginas);
			tabela.remove(pid);
			return true;
		}

		public void ps() {
			System.out.println("--- FILA READY ---");
			for(PCB p : ready) System.out.print(" " + p.id);
			System.out.println("\n--- FILA BLOCKED (I/O) ---");
			for(PCB p : blocked) System.out.print(" " + p.id);
			System.out.println("\n--- FILA BLOCKED (VM) ---");
			for(PCB p : blockedForVM) System.out.print(" " + p.id);
			System.out.println("\n--- RUNNING ---");
			if(running != null) System.out.print(" " + running.id);
			System.out.println("\n--- TABELA GERAL ---");

			System.out.println("PID  NOME        ESTADO    TAM_LOG");
			for (PCB pcb : tabela.values()) {
				System.out.printf("%-4d %-10s %-9s %d%n",
						pcb.id, pcb.nome, pcb.estado, pcb.tamLogico);
			}
		}

		public boolean dump(int pid) {
			PCB pcb = tabela.get(pid);
			if (pcb == null) return false;
			System.out.println("=== PCB ===");
			System.out.println("pid: " + pcb.id + "  nome: " + pcb.nome +
					"  estado: " + pcb.estado + "  tamLogico: " + pcb.tamLogico +
					"  pc: " + pcb.pc);
			System.out.println("--- Tabela de Páginas (PTE) ---");
			System.out.println("Page  Present  Dirty  Frame  DiskBlk");
			for (int i=0; i < pcb.tabelaPaginas.length; i++) {
				PageTableEntry pte = pcb.tabelaPaginas[i];
				System.out.printf("%-5d %-8b %-6b %-6d %-6d%n",
						i, pte.isPresent, pte.isDirty, pte.frameNum, pte.diskBlockNum);
			}
			System.out.println("=== Memória lógica do processo ===");
			utils.dumpLogical(pcb.tabelaPaginas, pcb.tamLogico);
			return true;
		}


		public boolean exec(int pid) {
			PCB pcb = tabela.get(pid);
			if (pcb == null) return false;

			ready.remove(pcb);


			while (tabela.containsKey(pid)) {
				if (pcb.estado == EstadoProc.TERMINATED) break;


				if (pcb.estado == EstadoProc.BLOCKED) {
					System.out.println("Processo " + pid + " bloqueado (I/O ou VM), 'exec' interrompido.");
					System.out.println("Use 'schedOn' para continuar a execução concorrente.");
					break;
				}


				ready.add(pcb);
				stepRoundRobin(pcb, 0);
			}
			return true;
		}


		public boolean existe(int pid) { return tabela.containsKey(pid); }
	}
	public class IORequest {
		public final int pid;
		public final int operacao;
		public final int endereco;

		public IORequest(int pid, int operacao, int endereco) {
			this.pid = pid;
			this.operacao = operacao;
			this.endereco = endereco;
		}
	}

	public class IODevice implements Runnable {
		private final HW hw;
		private final GP gp;
		private final java.util.concurrent.BlockingQueue<IORequest> fila =
				new java.util.concurrent.LinkedBlockingQueue<>();
		private Thread thread;

		public IODevice(HW hw, GP gp) {
			this.hw = hw;
			this.gp = gp;
		}

		public void start() {
			thread = new Thread(this, "IODevice");
			thread.start();
		}

		public void enqueue(IORequest req) {
			fila.add(req);
		}

		@Override
		public void run() {
			while (true) {
				try {
					IORequest req = fila.take();

					try { Thread.sleep(100); } catch (InterruptedException ignored) {}

					if (req.operacao == 1) {

						hw.mem.pos[req.endereco].opc = Opcode.DATA;
						hw.mem.pos[req.endereco].p   = 999;
						System.out.println("IN (async) pid=" + req.pid +
								" -> mem[" + req.endereco + "] = " +
								hw.mem.pos[req.endereco].p);
					} else if (req.operacao == 2) {
						System.out.println("OUT (async) pid=" + req.pid +
								"  valor=" + hw.mem.pos[req.endereco].p);
					}


					gp.ioCompleted(req.pid);
					if (hw.cpu.irpt == Interrupts.noInterrupt) {
						hw.cpu.irpt = Interrupts.intIO;
					}
				} catch (InterruptedException e) {

					break;
				}
			}
		}
	}


	public class PageIORequest {
		public enum Operation { READ_FROM_PROGRAM, WRITE_TO_SWAP, READ_FROM_SWAP }
		public final int pid;
		public final int pageNum;
		public final int frameNum;
		public final Operation op;
		public final String progName;

		public PageIORequest(int pid, int pageNum, int frameNum, Operation op, String progName) {
			this.pid = pid;
			this.pageNum = pageNum;
			this.frameNum = frameNum;
			this.op = op;
			this.progName = progName;
		}
	}


	public class VMDevice implements Runnable {
		private final HW hw;
		private final GP gp;
		private final Programs progs;
		private final Utilities utils;
		private final java.util.concurrent.BlockingQueue<PageIORequest> fila =
				new java.util.concurrent.LinkedBlockingQueue<>();
		private Thread thread;

		public VMDevice(HW hw, GP gp, Programs progs, Utilities utils) {
			this.hw = hw;
			this.gp = gp;
			this.progs = progs;
			this.utils = utils;
		}

		public void start() {
			thread = new Thread(this, "VMDevice (Disco)");
			thread.start();
		}

		public void enqueue(PageIORequest req) {
			fila.add(req);
		}

		@Override
		public void run() {
			while (true) {
				try {
					PageIORequest req = fila.take();


					try { Thread.sleep(250); } catch (InterruptedException ignored) {}

					PCB pcb = gp.getPCB(req.pid);

					if (pcb == null && req.op != PageIORequest.Operation.WRITE_TO_SWAP) {
						utils.getGM().freeFrame(req.frameNum);
						continue;
					}

					switch (req.op) {
						case READ_FROM_PROGRAM:
							Word[] img = progs.retrieveProgram(req.progName);
							if (img != null) {
								System.out.println("VMDevice: Lendo pág " + req.pageNum + " (pid " + req.pid + ") para frame " + req.frameNum);
								utils.loadSinglePage(img, req.pageNum, req.frameNum);

								gp.vmOperationCompleted(req.pid, req.pageNum, req.frameNum);
							} else {

								utils.getGM().freeFrame(req.frameNum);
							}
							break;

						case WRITE_TO_SWAP:


							System.out.println("VMDevice: Página " + req.pageNum + " (pid " + req.pid + ") salva no swap (do frame " + req.frameNum + ").");

							break;

						case READ_FROM_SWAP:

							System.out.println("VMDevice: Lendo pág " + req.pageNum + " (pid " + req.pid + ") do swap para frame " + req.frameNum);

							gp.vmOperationCompleted(req.pid, req.pageNum, req.frameNum);
							break;
					}

				} catch (InterruptedException e) {
					break;
				}
			}
		}
	}




	public HW hw;
	public SO so;
	public Programs progs;
	private IODevice io;
	private VMDevice vmDevice;



	private final int TAM_PG = 16;

	public Sistema(int tamMem) {
		// Inicializa o logger primeiro
		SystemLogger.initialize();

		hw = new HW(tamMem);
		progs = new Programs();
		so = new SO(hw, progs);
		hw.cpu.setUtilities(so.utils);


		io = new IODevice(hw, so.gp);
		io.start();


		vmDevice = so.vmDevice;
		vmDevice.start();
	}

	public void run() {
		java.util.Scanner in = new java.util.Scanner(System.in);
		System.out.println("SO pronto. Comandos: new <prog>, rm <pid>, ps, dump <pid>, dumpM <ini> <fim>, exec <pid>, traceOn, traceOff, execAll [quantum], schedOn [quantum], schedOff, exit");
		while (true) {
			System.out.print("> ");
			String line;
			try {
				line = in.nextLine();
			} catch (Exception e) {
				break;
			}
			if (line == null) break;
			line = line.trim();
			if (line.isEmpty()) continue;

			String[] t = line.split("\\s+");
			String cmd = t[0];

			try {
				switch (cmd) {
					case "new": {
						if (t.length < 2) { System.out.println("uso: new <nomeDePrograma>"); break; }
						String nome = t[1];
						Integer pid = so.gp.criaProcesso(nome, progs);
						if (pid == null) System.out.println("Erro: memoria insuficiente ou programa inexistente.");
						else System.out.println("Processo criado. pid " + pid);
						break;
					}
					case "rm": {
						if (t.length < 2) { System.out.println("uso: rm <pid>"); break; }
						int pid = Integer.parseInt(t[1]);
						boolean ok = so.gp.desalocaProcesso(pid);
						System.out.println(ok ? "Removido." : "PID inexistente.");
						break;
					}
					case "ps": {
						so.gp.ps();
						break;
					}
					case "dump": {
						if (t.length < 2) { System.out.println("uso: dump <pid>"); break; }
						int pid = Integer.parseInt(t[1]);
						boolean ok = so.gp.dump(pid);
						if (!ok) System.out.println("PID inexistente.");
						break;
					}
					case "dumpM": {
						if (t.length < 3) { System.out.println("uso: dumpM <ini> <fim>"); break; }
						int ini = Integer.parseInt(t[1]);
						int fim = Integer.parseInt(t[2]);
						so.utils.dump(ini, fim);
						break;
					}
					case "exec": {
						if (t.length < 2) { System.out.println("uso: exec <pid>"); break; }
						int pid = Integer.parseInt(t[1]);
						boolean ok = so.gp.exec(pid);
						if (!ok) System.out.println("PID inexistente.");
						break;
					}
					case "traceOn": {
						hw.cpu.setDebug(true);
						System.out.println("Trace ON.");
						break;
					}
					case "traceOff": {
						hw.cpu.setDebug(false);
						System.out.println("Trace OFF.");
						break;
					}
					case "exit": {
						so.gp.schedOff();
						SystemLogger.close();
						System.out.println("Saindo.");
						return;
					}
					case "execAll": {
						int q = (t.length >= 2 ? Integer.parseInt(t[1]) : 5);
						so.gp.execAll(q);
						break;
					}
					case "schedOn": {
						int q = (t.length >= 2 ? Integer.parseInt(t[1]) : 5);
						so.gp.schedOn(q);
						break;
					}
					case "schedOff": {
						so.gp.schedOff();
						break;
					}
					default:
						System.out.println("Comando desconhecido.");
				}
			} catch (Exception ex) {
				System.out.println("Erro: " + ex.getMessage());
				ex.printStackTrace();
			}
		}
	}



	public static void main(String args[]) {



		Sistema s = new Sistema(1024);
		s.run();
		SystemLogger.close();
	}



	public class Program {
		public String name;
		public Word[] image;

		public Program(String n, Word[] i) {
			name = n;
			image = i;
		}
	}

	public class Programs {

		public Word[] retrieveProgram(String pname) {
			for (Program p : progs) {
				if (p != null && p.name.equals(pname)) {
					return p.image;
				}
			}
			return null;
		}

		public Program[] progs = {
				new Program("fatorial",
						new Word[] {


								new Word(Opcode.LDI, 0, -1, 7),
								new Word(Opcode.LDI, 1, -1, 1),
								new Word(Opcode.LDI, 6, -1, 1),
								new Word(Opcode.LDI, 7, -1, 8),
								new Word(Opcode.JMPIE, 7, 0, 0),
								new Word(Opcode.MULT, 1, 0, -1),
								new Word(Opcode.SUB, 0, 6, -1),

								new Word(Opcode.JMP, -1, -1, 4),
								new Word(Opcode.STD, 1, -1, 10),
								new Word(Opcode.STOP, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1)
						}),

				new Program("fatorialV2",
						new Word[] {
								new Word(Opcode.LDI, 0, -1, 5),
								new Word(Opcode.STD, 0, -1, 19),
								new Word(Opcode.LDD, 0, -1, 19),
								new Word(Opcode.LDI, 1, -1, -1),
								new Word(Opcode.LDI, 2, -1, 13),
								new Word(Opcode.JMPIL, 2, 0, -1),
								new Word(Opcode.LDI, 1, -1, 1),
								new Word(Opcode.LDI, 6, -1, 1),
								new Word(Opcode.LDI, 7, -1, 13),
								new Word(Opcode.JMPIE, 7, 0, 0),
								new Word(Opcode.MULT, 1, 0, -1),
								new Word(Opcode.SUB, 0, 6, -1),
								new Word(Opcode.JMP, -1, -1, 9),
								new Word(Opcode.STD, 1, -1, 18),
								new Word(Opcode.LDI, 8, -1, 2),
								new Word(Opcode.LDI, 9, -1, 18),
								new Word(Opcode.SYSCALL, -1, -1, -1),
								new Word(Opcode.STOP, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1) }
				),

				new Program("progMinimo",
						new Word[] {
								new Word(Opcode.LDI, 0, -1, 999),
								new Word(Opcode.STD, 0, -1, 8),
								new Word(Opcode.STD, 0, -1, 9),
								new Word(Opcode.STD, 0, -1, 10),
								new Word(Opcode.STD, 0, -1, 11),
								new Word(Opcode.STD, 0, -1, 12),
								new Word(Opcode.STOP, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1)
						}),

				new Program("fibonacci10",
						new Word[] {
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.STD, 1, -1, 20),
								new Word(Opcode.LDI, 2, -1, 1),
								new Word(Opcode.STD, 2, -1, 21),
								new Word(Opcode.LDI, 0, -1, 22),
								new Word(Opcode.LDI, 6, -1, 6),
								new Word(Opcode.LDI, 7, -1, 31),
								new Word(Opcode.LDI, 3, -1, 0),
								new Word(Opcode.ADD, 3, 1, -1),
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.ADD, 1, 2, -1),
								new Word(Opcode.ADD, 2, 3, -1),
								new Word(Opcode.STX, 0, 2, -1),
								new Word(Opcode.ADDI, 0, -1, 1),
								new Word(Opcode.SUB, 7, 0, -1),
								new Word(Opcode.JMPIG, 6, 7, -1),
								new Word(Opcode.STOP, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1)
						}),

				new Program("fibonacci10v2",
						new Word[] {
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.STD, 1, -1, 20),
								new Word(Opcode.LDI, 2, -1, 1),
								new Word(Opcode.STD, 2, -1, 21),
								new Word(Opcode.LDI, 0, -1, 22),
								new Word(Opcode.LDI, 6, -1, 6),
								new Word(Opcode.LDI, 7, -1, 31),
								new Word(Opcode.MOVE, 3, 1, -1),
								new Word(Opcode.MOVE, 1, 2, -1),
								new Word(Opcode.ADD, 2, 3, -1),
								new Word(Opcode.STX, 0, 2, -1),
								new Word(Opcode.ADDI, 0, -1, 1),
								new Word(Opcode.SUB, 7, 0, -1),
								new Word(Opcode.JMPIG, 6, 7, -1),
								new Word(Opcode.STOP, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1)
						}),
				new Program("fibonacciREAD",
						new Word[] {

								new Word(Opcode.LDI, 8, -1, 1),
								new Word(Opcode.LDI, 9, -1, 55),

								new Word(Opcode.SYSCALL, -1, -1, -1),
								new Word(Opcode.LDD, 7, -1, 55),
								new Word(Opcode.LDI, 3, -1, 0),
								new Word(Opcode.ADD, 3, 7, -1),
								new Word(Opcode.LDI, 4, -1, 36),
								new Word(Opcode.LDI, 1, -1, -1),
								new Word(Opcode.STD, 1, -1, 41),
								new Word(Opcode.JMPIL, 4, 7, -1),
								new Word(Opcode.JMPIE, 4, 7, -1),
								new Word(Opcode.ADDI, 7, -1, 41),
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.STD, 1, -1, 41),

								new Word(Opcode.SUBI, 3, -1, 1),
								new Word(Opcode.JMPIE, 4, 3, -1),
								new Word(Opcode.ADDI, 3, -1, 1),
								new Word(Opcode.LDI, 2, -1, 1),
								new Word(Opcode.STD, 2, -1, 42),
								new Word(Opcode.SUBI, 3, -1, 2),
								new Word(Opcode.JMPIE, 4, 3, -1),
								new Word(Opcode.LDI, 0, -1, 43),
								new Word(Opcode.LDI, 6, -1, 25),
								new Word(Opcode.LDI, 5, -1, 0),
								new Word(Opcode.ADD, 5, 7, -1),
								new Word(Opcode.LDI, 7, -1, 0),
								new Word(Opcode.ADD, 7, 5, -1),
								new Word(Opcode.LDI, 3, -1, 0),
								new Word(Opcode.ADD, 3, 1, -1),
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.ADD, 1, 2, -1),
								new Word(Opcode.ADD, 2, 3, -1),
								new Word(Opcode.STX, 0, 2, -1),
								new Word(Opcode.ADDI, 0, -1, 1),
								new Word(Opcode.SUB, 7, 0, -1),
								new Word(Opcode.JMPIG, 6, 7, -1),
								new Word(Opcode.STOP, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1)
						}),
				new Program("PB",
						new Word[] {


								new Word(Opcode.LDI, 0, -1, 7),
								new Word(Opcode.STD, 0, -1, 50),
								new Word(Opcode.LDD, 0, -1, 50),
								new Word(Opcode.LDI, 1, -1, -1),
								new Word(Opcode.LDI, 2, -1, 13),
								new Word(Opcode.JMPIL, 2, 0, -1),
								new Word(Opcode.LDI, 1, -1, 1),
								new Word(Opcode.LDI, 6, -1, 1),
								new Word(Opcode.LDI, 7, -1, 13),
								new Word(Opcode.JMPIE, 7, 0, 0),
								new Word(Opcode.MULT, 1, 0, -1),
								new Word(Opcode.SUB, 0, 6, -1),
								new Word(Opcode.JMP, -1, -1, 9),
								new Word(Opcode.STD, 1, -1, 15),
								new Word(Opcode.STOP, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1)
						}),
				new Program("PC",
						new Word[] {




								new Word(Opcode.LDI, 7, -1, 5),
								new Word(Opcode.LDI, 6, -1, 5),
								new Word(Opcode.LDI, 5, -1, 46),
								new Word(Opcode.LDI, 4, -1, 47),
								new Word(Opcode.LDI, 0, -1, 4),
								new Word(Opcode.STD, 0, -1, 46),
								new Word(Opcode.LDI, 0, -1, 3),
								new Word(Opcode.STD, 0, -1, 47),
								new Word(Opcode.LDI, 0, -1, 5),
								new Word(Opcode.STD, 0, -1, 48),
								new Word(Opcode.LDI, 0, -1, 1),
								new Word(Opcode.STD, 0, -1, 49),
								new Word(Opcode.LDI, 0, -1, 2),
								new Word(Opcode.STD, 0, -1, 50),
								new Word(Opcode.LDI, 3, -1, 25),
								new Word(Opcode.STD, 3, -1, 99),
								new Word(Opcode.LDI, 3, -1, 22),
								new Word(Opcode.STD, 3, -1, 98),
								new Word(Opcode.LDI, 3, -1, 38),
								new Word(Opcode.STD, 3, -1, 97),
								new Word(Opcode.LDI, 3, -1, 25),
								new Word(Opcode.STD, 3, -1, 96),
								new Word(Opcode.LDI, 6, -1, 0),
								new Word(Opcode.ADD, 6, 7, -1),
								new Word(Opcode.SUBI, 6, -1, 1),
								new Word(Opcode.JMPIEM, -1, 6, 97),

								new Word(Opcode.LDX, 0, 5, -1),

								new Word(Opcode.LDX, 1, 4, -1),
								new Word(Opcode.LDI, 2, -1, 0),
								new Word(Opcode.ADD, 2, 0, -1),
								new Word(Opcode.SUB, 2, 1, -1),
								new Word(Opcode.ADDI, 4, -1, 1),
								new Word(Opcode.SUBI, 6, -1, 1),
								new Word(Opcode.JMPILM, -1, 2, 99),
								new Word(Opcode.STX, 5, 1, -1),
								new Word(Opcode.SUBI, 4, -1, 1),
								new Word(Opcode.STX, 4, 0, -1),
								new Word(Opcode.ADDI, 4, -1, 1),
								new Word(Opcode.JMPIGM, -1, 6, 99),
								new Word(Opcode.ADDI, 5, -1, 1),
								new Word(Opcode.SUBI, 7, -1, 1),
								new Word(Opcode.LDI, 4, -1, 0),
								new Word(Opcode.ADD, 4, 5, -1),
								new Word(Opcode.ADDI, 4, -1, 1),
								new Word(Opcode.JMPIGM, -1, 7, 98),
								new Word(Opcode.STOP, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1)
						})
		};
	}
}