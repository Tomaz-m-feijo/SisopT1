// PUCRS - Escola Politécnica - Sistemas Operacionais
// Prof. Fernando Dotti
// Código fornecido como parte da solução do projeto de Sistemas Operacionais
//
// Estrutura deste código:
//    Todo código está dentro da classe *Sistema*
//    Dentro de Sistema, encontra-se acima a definição de HW:
//           Memory,  Word, 
//           CPU tem Opcodes (codigos de operacoes suportadas na cpu),
//               e Interrupcoes possíveis, define o que executa para cada instrucao
//           VM -  a máquina virtual é uma instanciação de CPU e Memória
//    Depois as definições de SW:
//           no momento são esqueletos (so estrutura) para
//					InterruptHandling    e
//					SysCallHandling 
//    A seguir temos utilitários para usar o sistema
//           carga, início de execução e dump de memória
//    Por último os programas existentes, que podem ser copiados em memória.
//           Isto representa programas armazenados.
//    Veja o main.  Ele instancia o Sistema com os elementos mencionados acima.
//           em seguida solicita a execução de algum programa com  loadAndExec

public class Sistema {

	// -------------------------------------------------------------------------------------------------------
	// --------------------- H A R D W A R E - definicoes de HW
	// ----------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// --------------------- M E M O R I A - definicoes de palavra de memoria,
	// memória ----------------------

	public class Memory {
		public Word[] pos; // pos[i] é a posição i da memória. cada posição é uma palavra.

		public Memory(int size) {
			pos = new Word[size];
			for (int i = 0; i < pos.length; i++) {
				pos[i] = new Word(Opcode.___, -1, -1, -1);
			}
			; // cada posicao da memoria inicializada
		}
	}

	public class Word {    // cada posicao da memoria tem uma instrucao (ou um dado)
		public Opcode opc; //
		public int ra;     // indice do primeiro registrador da operacao (Rs ou Rd cfe opcode na tabela)
		public int rb;     // indice do segundo registrador da operacao (Rc ou Rs cfe operacao)
		public int p;      // parametro para instrucao (k ou A cfe operacao), ou o dado, se opcode = DADO

		public Word(Opcode _opc, int _ra, int _rb, int _p) { // vide definição da VM - colunas vermelhas da tabela
			opc = _opc;
			ra = _ra;
			rb = _rb;
			p  = _p;
		}
	}

	// -------------------------------------------------------------------------------------------------------
	// --------------------- C P U - definicoes da CPU
	// -----------------------------------------------------

	public enum Opcode {
		DATA, ___,              // se memoria nesta posicao tem um dado, usa DATA, se nao usada ee NULO ___
		JMP, JMPI, JMPIG, JMPIL, JMPIE, // desvios
		JMPIM, JMPIGM, JMPILM, JMPIEM,
		JMPIGK, JMPILK, JMPIEK, JMPIGT,
		ADDI, SUBI, ADD, SUB, MULT,    // matematicos
		LDI, LDD, STD, LDX, STX, MOVE, // movimentacao
		SYSCALL, STOP                  // chamada de sistema e parada
	}

	public enum Interrupts {           // possiveis interrupcoes que esta CPU gera
		noInterrupt, intEnderecoInvalido, intInstrucaoInvalida, intOverflow;
	}

	public class CPU {
		private int maxInt; // valores maximo e minimo para inteiros nesta cpu
		private int minInt;
		                    // CONTEXTO da CPU ...
		private int pc;     // ... composto de program counter,
		private Word ir;    // instruction register,
		private int[] reg;  // registradores da CPU
		private Interrupts irpt; // durante instrucao, interrupcao pode ser sinalizada
		                    // FIM CONTEXTO DA CPU: tudo que precisa sobre o estado de um processo para
		                    // executa-lo
		                    // nas proximas versoes isto pode modificar

		private Word[] m;   // m é o array de memória "física", CPU tem uma ref a m para acessar
		// ======== MMU (tabela de paginas corrente) ========
		private int[] pageTable; // tabela de páginas do processo atual (page -> frame)
		private int tamPag;      // tamanho da página em palavras


		private InterruptHandling ih;    // significa desvio para rotinas de tratamento de Int - se int ligada, desvia
		private SysCallHandling sysCall; // significa desvio para tratamento de chamadas de sistema

		private boolean cpuStop;    // flag para parar CPU - caso de interrupcao que acaba o processo, ou chamada stop - 
									// nesta versao acaba o sistema no fim do prog

		                            // auxilio aa depuração
		private boolean debug;      // se true entao mostra cada instrucao em execucao
		private Utilities u;        // para debug (dump)

		public CPU(Memory _mem, boolean _debug) { // ref a MEMORIA passada na criacao da CPU
			maxInt = 32767;            // capacidade de representacao modelada
			minInt = -32767;           // se exceder deve gerar interrupcao de overflow
			m = _mem.pos;              // usa o atributo 'm' para acessar a memoria, só para ficar mais pratico
			reg = new int[10];         // aloca o espaço dos registradores - regs 8 e 9 usados somente para IO

			debug = _debug;            // se true, print da instrucao em execucao

		}

		public void setAddressOfHandlers(InterruptHandling _ih, SysCallHandling _sysCall) {
			ih = _ih;                  // aponta para rotinas de tratamento de int
			sysCall = _sysCall;        // aponta para rotinas de tratamento de chamadas de sistema
		}

		public void setUtilities(Utilities _u) {
			u = _u;                     // aponta para rotinas utilitárias - fazer dump da memória na tela
		}

		public void setDebug(boolean on) {
			this.debug = on;
		}
                                       // verificação de enderecamento

		private boolean legal(int e) {
			return phys(e) >= 0;
		}

		private boolean testOverflow(int v) {             // toda operacao matematica deve avaliar se ocorre overflow
			if ((v < minInt) || (v > maxInt)) {
				irpt = Interrupts.intOverflow;            // se houver liga interrupcao no meio da exec da instrucao
				return false;
			}
			;
			return true;
		}

		public void setContext(int _pc) {                 // usado para setar o contexto da cpu para rodar um processo
			                                              // [ nesta versao é somente colocar o PC na posicao 0 ]
			pc = _pc;                                     // pc cfe endereco logico
			irpt = Interrupts.noInterrupt;                // reset da interrupcao registrada
		}

		//metodos MMU
		public void setMMU(int[] _pageTable, int _tamPag) {
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
			int frame = pageTable[page];
			int phys  = frame * tamPag + off;
			if (phys < 0 || phys >= m.length) {
				irpt = Interrupts.intEnderecoInvalido;
				return -1;
			}
			return phys;
		}

		public void run() {                               // execucao da CPU supoe que o contexto da CPU, vide acima,
														  // esta devidamente setado
			cpuStop = false;
			while (!cpuStop) {      // ciclo de instrucoes. acaba cfe resultado da exec da instrucao, veja cada caso.

				// --------------------------------------------------------------------------------------------------
				// FASE DE FETCH
				if (legal(pc)) { // pc valido
					ir =  m[phys(pc)];;  // <<<<<<<<<<<< AQUI faz FETCH - busca posicao da memoria apontada por pc, guarda em ir
					             // resto é dump de debug
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

				// --------------------------------------------------------------------------------------------------
				// FASE DE EXECUCAO DA INSTRUCAO CARREGADA NO ir
					switch (ir.opc) {       // conforme o opcode (código de operação) executa

						// Instrucoes de Busca e Armazenamento em Memoria
						case LDI: // Rd ← k        veja a tabela de instrucoes do HW simulado para entender a semantica da instrucao
							reg[ir.ra] = ir.p;
							pc++;
							break;
						case LDD: // Rd <- [A]
							if (legal(ir.p)) {
								reg[ir.ra] = m[phys(ir.p)].p;
								pc++;
							}
							break;
						case LDX: // RD <- [RS] // NOVA
							if (legal(reg[ir.rb])) {
								reg[ir.ra] = m[phys(reg[ir.rb])].p;
								pc++;
							}
							break;
						case STD: // [A] ← Rs
							if (legal(ir.p)) {
								int __a = phys(ir.p);
								m[__a].opc = Opcode.DATA;
								m[__a].p = reg[ir.ra];
								pc++;
								if (debug) {
									System.out.print("                                  ");
									u.dump(__a, __a + 1);
								}
							}
							break;
						case STX: // [Rd] ← Rs
							if (legal(reg[ir.ra])) {
								int __a = phys(reg[ir.ra]);
								m[__a].opc = Opcode.DATA;
								m[__a].p = reg[ir.rb];
								pc++;
							}
							break;
						case MOVE: // RD <- RS
							reg[ir.ra] = reg[ir.rb];
							pc++;
							break;
						// Instrucoes Aritmeticas
						case ADD: // Rd ← Rd + Rs
							reg[ir.ra] = reg[ir.ra] + reg[ir.rb];
							testOverflow(reg[ir.ra]);
							pc++;
							break;
						case ADDI: // Rd ← Rd + k
							reg[ir.ra] = reg[ir.ra] + ir.p;
							testOverflow(reg[ir.ra]);
							pc++;
							break;
						case SUB: // Rd ← Rd - Rs
							reg[ir.ra] = reg[ir.ra] - reg[ir.rb];
							testOverflow(reg[ir.ra]);
							pc++;
							break;
						case SUBI: // RD <- RD - k // NOVA
							reg[ir.ra] = reg[ir.ra] - ir.p;
							testOverflow(reg[ir.ra]);
							pc++;
							break;
						case MULT: // Rd <- Rd * Rs
							reg[ir.ra] = reg[ir.ra] * reg[ir.rb];
							testOverflow(reg[ir.ra]);
							pc++;
							break;

						// Instrucoes JUMP
						case JMP: // PC <- k
							pc = ir.p;
							break;
						case JMPIM: // PC <- [A]
							pc = m[phys(ir.p)].p;
							break;
						case JMPIG: // If Rc > 0 Then PC ← Rs Else PC ← PC +1
							if (reg[ir.rb] > 0) {
								pc = reg[ir.ra];
							} else {
								pc++;
							}
							break;
						case JMPIGK: // If RC > 0 then PC <- k else PC++
							if (reg[ir.rb] > 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;
						case JMPILK: // If RC < 0 then PC <- k else PC++
							if (reg[ir.rb] < 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;
						case JMPIEK: // If RC = 0 then PC <- k else PC++
							if (reg[ir.rb] == 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;
						case JMPIL: // if Rc < 0 then PC <- Rs Else PC <- PC +1
							if (reg[ir.rb] < 0) {
								pc = reg[ir.ra];
							} else {
								pc++;
							}
							break;
						case JMPIE: // If Rc = 0 Then PC <- Rs Else PC <- PC +1
							if (reg[ir.rb] == 0) {
								pc = reg[ir.ra];
							} else {
								pc++;
							}
							break;
						case JMPIGM:
							if (legal(ir.p)) {
								if (reg[ir.rb] > 0) { pc = m[phys(ir.p)].p; } else { pc++; }
							}
							break;
						case JMPILM:
							if (reg[ir.rb] < 0) { pc = m[phys(ir.p)].p; } else { pc++; }
							break;
						case JMPIEM:
							if (reg[ir.rb] == 0) { pc = m[phys(ir.p)].p; } else { pc++; }
							break;
						case JMPIGT: // If RS>RC then PC <- k else PC++
							if (reg[ir.ra] > reg[ir.rb]) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;

						case DATA: // pc está sobre área supostamente de dados
							irpt = Interrupts.intInstrucaoInvalida;
							break;

						// Chamadas de sistema
						case SYSCALL:
							sysCall.handle(); // <<<<< aqui desvia para rotina de chamada de sistema, no momento so
												// temos IO
							pc++;
							break;

						case STOP: // por enquanto, para execucao
							sysCall.stop();
							cpuStop = true;
							break;

						// Inexistente
						default:
							irpt = Interrupts.intInstrucaoInvalida;
							break;
					}
				}
				// --------------------------------------------------------------------------------------------------
				// VERIFICA INTERRUPÇÃO !!! - TERCEIRA FASE DO CICLO DE INSTRUÇÕES
				if (irpt != Interrupts.noInterrupt) { // existe interrupção
					ih.handle(irpt);                  // desvia para rotina de tratamento - esta rotina é do SO
					cpuStop = true;                   // nesta versao, para a CPU
				}
			} // FIM DO CICLO DE UMA INSTRUÇÃO
		}
	}
	// ------------------ C P U - fim
	// -----------------------------------------------------------------------
	// ------------------------------------------------------------------------------------------------------

	// ------------------- HW - constituido de CPU e MEMORIA
	// -----------------------------------------------
	public class HW {
		public Memory mem;
		public CPU cpu;

		public HW(int tamMem) {
			mem = new Memory(tamMem);
			cpu = new CPU(mem, true); // true liga debug
		}
	}

	// ===== GM – Interface e Implementacao (paginacao) =====
	private interface GM {
		// Retorna a tabela de páginas (page -> frame) ou null se não houver frames suficientes
		int[] aloca(int nroPalavras);
		void desaloca(int[] tabelaPaginas);
	}

	private class GerenteMemoriaPaginado implements GM {
		private final int tamPg;
		private final int nFrames;
		private final boolean[] livre;

		GerenteMemoriaPaginado(int tamMem, int tamPg) {
			this.tamPg = tamPg;
			this.nFrames = tamMem / tamPg;
			this.livre = new boolean[nFrames];
			for (int i = 0; i < nFrames; i++) livre[i] = true;
		}

		@Override
		public int[] aloca(int nroPalavras) {
			int nPag = (nroPalavras + tamPg - 1) / tamPg;
			int[] tabela = new int[nPag];
			int j = 0;
			for (int f = 0; f < nFrames && j < nPag; f++) {
				if (livre[f]) {
					livre[f] = false;
					tabela[j++] = f;
				}
			}
			if (j < nPag) { // rollback
				for (int k = 0; k < j; k++) livre[tabela[k]] = true;
				return null;
			}
			return tabela;
		}

		@Override
		public void desaloca(int[] tabelaPaginas) {
			if (tabelaPaginas == null) return;
			for (int page = 0; page < tabelaPaginas.length; page++) {
				int f = tabelaPaginas[page];
				if (f >= 0 && f < nFrames) livre[f] = true;
			}
		}
	}
	// -------------------------------------------------------------------------------------------------------

	// --------------------H A R D W A R E - fim
	// -------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// ///////////////////////////////////////////////////////////////////////////////////////////////////////

	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// ------------------- SW - inicio - Sistema Operacional
	// -------------------------------------------------

	// ------------------- I N T E R R U P C O E S - rotinas de tratamento
	// ----------------------------------
	public class InterruptHandling {
		private HW hw; // referencia ao hw se tiver que setar algo

		public InterruptHandling(HW _hw) {
			hw = _hw;
		}

		public void handle(Interrupts irpt) {
			// apenas avisa - todas interrupcoes neste momento finalizam o programa
			System.out.println(
					"                                               Interrupcao " + irpt + "   pc: " + hw.cpu.pc);
		}
	}

	// ------------------- C H A M A D A S D E S I S T E M A - rotinas de tratamento
	// ----------------------
	public class SysCallHandling {
		private HW hw; // referencia ao hw se tiver que setar algo

		public SysCallHandling(HW _hw) {
			hw = _hw;
		}

		public void stop() { // chamada de sistema indicando final de programa
							 // nesta versao cpu simplesmente pára
			System.out.println("                                               SYSCALL STOP");
		}

		public void handle() { // chamada de sistema 
			                   // suporta somente IO, com parametros 
							   // reg[8] = in ou out    e reg[9] endereco do inteiro
			System.out.println("SYSCALL pars:  " + hw.cpu.reg[8] + " / " + hw.cpu.reg[9]);

			if  (hw.cpu.reg[8]==1){
				  // leitura ...

			} else if (hw.cpu.reg[8]==2){
				  // escrita - escreve o conteuodo da memoria na posicao dada em reg[9]
				  System.out.println("OUT:   "+ hw.mem.pos[hw.cpu.reg[9]].p);
			} else {System.out.println("  PARAMETRO INVALIDO"); }		
		}
	}

	// ------------------ U T I L I T A R I O S D O S I S T E M A
	// -----------------------------------------
	// ------------------ load é invocado a partir de requisição do usuário

	// carga na memória
	public class Utilities {
		private HW hw;
		private GM gm;

		public GM getGM() { return gm; }

		public Utilities(HW _hw) {
			hw = _hw;
			gm = new GerenteMemoriaPaginado(hw.mem.pos.length, TAM_PG);
		}

		//nao vai ser usado
		private void loadProgram(Word[] p) {
			Word[] m = hw.mem.pos; // m[] é o array de posições memória do hw
			for (int i = 0; i < p.length; i++) {
				m[i].opc = p[i].opc;
				m[i].ra = p[i].ra;
				m[i].rb = p[i].rb;
				m[i].p = p[i].p;
			}
		}

		public void loadProgramPaged(Word[] p, int[] tabelaPaginas) {
			Word[] m = hw.mem.pos;
			for (int i = 0; i < p.length; i++) {
				int page = i / TAM_PG;
				int off  = i % TAM_PG;
				int phys = tabelaPaginas[page] * TAM_PG + off;
				m[phys].opc = p[i].opc;
				m[phys].ra  = p[i].ra;
				m[phys].rb  = p[i].rb;
				m[phys].p   = p[i].p;
			}
		}

		// dump da memória
		public void dump(Word w) { // funcoes de DUMP nao existem em hardware - colocadas aqui para facilidade
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
			Word[] m = hw.mem.pos; // m[] é o array de posições memória do hw
			for (int i = ini; i < fim; i++) {
				System.out.print(i);
				System.out.print(":  ");
				dump(m[i]);
			}
		}

		public void dumpLogical(int[] tabelaPaginas, int tamProg) {
			Word[] m = hw.mem.pos;
			for (int i = 0; i < tamProg; i++) {
				int page = i / TAM_PG;
				int off  = i % TAM_PG;
				int phys = tabelaPaginas[page] * TAM_PG + off;
				System.out.print(i);
				System.out.print(":  ");
				dump(m[phys]);
			}
		}

		private void loadAndExec(Word[] p) {
			int[] tabela = gm.aloca(p.length);
			if (tabela == null) {
				throw new RuntimeException("Memória insuficiente para carregar o programa.");
			}

			loadProgramPaged(p, tabela);
			hw.cpu.setMMU(tabela, TAM_PG);

			System.out.println("---------------------------------- programa carregado (paginado)");
			dumpLogical(tabela, p.length);

			hw.cpu.setContext(0);
			System.out.println("---------------------------------- inicia execucao ");
			hw.cpu.run();

			System.out.println("---------------------------------- memoria (logica) apos execucao ");
			dumpLogical(tabela, p.length);

			gm.desaloca(tabela);
		}
	}

	public class SO {
		public InterruptHandling ih;
		public SysCallHandling sc;
		public Utilities utils;
		public GP gp;
		public SO(HW hw) {
			ih = new InterruptHandling(hw); // rotinas de tratamento de int
			sc = new SysCallHandling(hw); // chamadas de sistema
			hw.cpu.setAddressOfHandlers(ih, sc);
			utils = new Utilities(hw);
			gp = new GP(hw, utils);
		}
	}
	public enum EstadoProc { READY, RUNNING, TERMINATED }

	public class PCB {
		public final int id;
		public final String nome;
		public final int[] tabelaPaginas;
		public final int tamPag;
		public final int tamLogico;     // quantas palavras lógicas reservadas ao processo
		public int pc;                  // para evoluções futuras (ctx switch)
		public EstadoProc estado;

		public PCB(int id, String nome, int[] tabelaPaginas, int tamPag, int tamLogico) {
			this.id = id;
			this.nome = nome;
			this.tabelaPaginas = tabelaPaginas;
			this.tamPag = tamPag;
			this.tamLogico = tamLogico;
			this.pc = 0;
			this.estado = EstadoProc.READY;
		}
	}

	// ===== GP – Gerente de Processos =====
	public class GP {
		private final HW hw;
		private final Utilities utils;
		private final GM gm;

		private java.util.Map<Integer, PCB> tabela = new java.util.LinkedHashMap<>();
		private java.util.Queue<PCB> ready = new java.util.ArrayDeque<>();
		private PCB running = null;
		private int nextPid = 1;

		public GP(HW hw, Utilities utils) {
			this.hw = hw;
			this.utils = utils;
			this.gm = utils.getGM(); // reutiliza o mesmo GM da Utilities
		}

		// cria processo e coloca na fila de prontos
		public Integer criaProcesso(String progName, Programs programs) {
			Word[] img = programs.retrieveProgram(progName);
			if (img == null) return null;

			// tamanho lógico padrão = tamanho da imagem
			int tamLogico = img.length;

			// (opcional, mas recomendado p/ teu "PC"): reserva espaço lógico extra
			// para endereços altos que ele usa (96..99). Assim não dá intEnderecoInvalido.
			if ("PC".equals(progName) && tamLogico < 100) {
				tamLogico = 100;
			}

			int[] tabelaPaginas = gm.aloca(tamLogico);
			if (tabelaPaginas == null) return null;

			utils.loadProgramPaged(img, tabelaPaginas);

			int pid = nextPid++;
			PCB pcb = new PCB(pid, progName, tabelaPaginas, TAM_PG, tamLogico);
			tabela.put(pid, pcb);
			ready.add(pcb);
			return pid;
		}

		// desaloca e remove processo (de qualquer fila/estado)
		public boolean desalocaProcesso(int pid) {
			PCB pcb = tabela.get(pid);
			if (pcb == null) return false;
			// se estiver na ready, remove
			ready.remove(pcb);
			// se estiver rodando, "para"
			if (running == pcb) {
				running = null;
			}
			// libera memória e retira da tabela
			gm.desaloca(pcb.tabelaPaginas);
			tabela.remove(pid);
			return true;
		}

		public void ps() {
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
					"  estado: " + pcb.estado + "  tamLogico: " + pcb.tamLogico);
			System.out.println("tabelaPaginas (page->frame): " + java.util.Arrays.toString(pcb.tabelaPaginas));
			System.out.println("=== Memória lógica do processo ===");
			utils.dumpLogical(pcb.tabelaPaginas, pcb.tamLogico);
			return true;
		}

		// executa o processo por id
		public boolean exec(int pid) {
			PCB pcb = tabela.get(pid);
			if (pcb == null) return false;
			running = pcb;
			pcb.estado = EstadoProc.RUNNING;

			// carrega a MMU e contexto
			hw.cpu.setMMU(pcb.tabelaPaginas, pcb.tamPag);
			hw.cpu.setContext(pcb.pc);

			System.out.println("---------------------------------- inicia execucao (pid " + pid + ")");
			hw.cpu.run();
			System.out.println("---------------------------------- fim execucao (pid " + pid + ")");

			// nesta fase, consideramos o processo encerrado (STOP) -> TERMINATED
			pcb.estado = EstadoProc.TERMINATED;
			running = null;
			return true;
		}

		// utilitário para a shell
		public boolean existe(int pid) { return tabela.containsKey(pid); }
	}
	// -------------------------------------------------------------------------------------------------------
	// ------------------- S I S T E M A
	// --------------------------------------------------------------------

	public HW hw;
	public SO so;
	public Programs progs;

	// ======== PARAMETRO DO GERENTE DE MEMORIA ========
	// Tamanho da página em "palavras"
	private final int TAM_PG = 1000;

	public Sistema(int tamMem) {
		hw = new HW(tamMem);           // memoria do HW tem tamMem palavras
		so = new SO(hw);
		hw.cpu.setUtilities(so.utils); // permite cpu fazer dump de memoria ao avancar
		progs = new Programs();
	}

	public void run() {
		java.util.Scanner in = new java.util.Scanner(System.in);
		System.out.println("SO pronto. Comandos: new <prog>, rm <pid>, ps, dump <pid>, dumpM <ini> <fim>, exec <pid>, traceOn, traceOff, exit");
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
						System.out.println("Saindo.");
						return;
					}
					default:
						System.out.println("Comando desconhecido.");
				}
			} catch (Exception ex) {
				System.out.println("Erro: " + ex.getMessage());
			}
		}
	}

	// ------------------- S I S T E M A - fim
	// --------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// ------------------- instancia e testa sistema
	public static void main(String args[]) {
		Sistema s = new Sistema(255555);
		s.run();
	}

	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// --------------- P R O G R A M A S - não fazem parte do sistema
	// esta classe representa programas armazenados (como se estivessem em disco)
	// que podem ser carregados para a memória (load faz isto)

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
								// este fatorial so aceita valores positivos. nao pode ser zero
								// linha coment
								new Word(Opcode.LDI, 0, -1, 7), // 0 r0 é valor a calcular fatorial
								new Word(Opcode.LDI, 1, -1, 1), // 1 r1 é 1 para multiplicar (por r0)
								new Word(Opcode.LDI, 6, -1, 1), // 2 r6 é 1 o decremento
								new Word(Opcode.LDI, 7, -1, 8), // 3 r7 tem posicao 8 para fim do programa
								new Word(Opcode.JMPIE, 7, 0, 0), // 4 se r0=0 pula para r7(=8)
								new Word(Opcode.MULT, 1, 0, -1), // 5 r1 = r1 * r0 (r1 acumula o produto por cada termo)
								new Word(Opcode.SUB, 0, 6, -1), // 6 r0 = r0 - r6 (r6=1) decrementa r0 para proximo
																// termo
								new Word(Opcode.JMP, -1, -1, 4), // 7 vai p posicao 4
								new Word(Opcode.STD, 1, -1, 10), // 8 coloca valor de r1 na posição 10
								new Word(Opcode.STOP, -1, -1, -1), // 9 stop
								new Word(Opcode.DATA, -1, -1, -1) // 10 ao final o valor está na posição 10 da memória
						}),

				new Program("fatorialV2",
						new Word[] {
								new Word(Opcode.LDI, 0, -1, 5), // numero para colocar na memoria, ou pode ser lido
								new Word(Opcode.STD, 0, -1, 19),
								new Word(Opcode.LDD, 0, -1, 19),
								new Word(Opcode.LDI, 1, -1, -1),
								new Word(Opcode.LDI, 2, -1, 13), // SALVAR POS STOP
								new Word(Opcode.JMPIL, 2, 0, -1), // caso negativo pula pro STD
								new Word(Opcode.LDI, 1, -1, 1),
								new Word(Opcode.LDI, 6, -1, 1),
								new Word(Opcode.LDI, 7, -1, 13),
								new Word(Opcode.JMPIE, 7, 0, 0), // POS 9 pula para STD (Stop-1)
								new Word(Opcode.MULT, 1, 0, -1),
								new Word(Opcode.SUB, 0, 6, -1),
								new Word(Opcode.JMP, -1, -1, 9), // pula para o JMPIE
								new Word(Opcode.STD, 1, -1, 18),
								new Word(Opcode.LDI, 8, -1, 2), // escrita
								new Word(Opcode.LDI, 9, -1, 18), // endereco com valor a escrever
								new Word(Opcode.SYSCALL, -1, -1, -1),
								new Word(Opcode.STOP, -1, -1, -1), // POS 17
								new Word(Opcode.DATA, -1, -1, -1), // POS 18
								new Word(Opcode.DATA, -1, -1, -1) } // POS 19
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
								new Word(Opcode.DATA, -1, -1, -1), // 7
								new Word(Opcode.DATA, -1, -1, -1), // 8
								new Word(Opcode.DATA, -1, -1, -1), // 9
								new Word(Opcode.DATA, -1, -1, -1), // 10
								new Word(Opcode.DATA, -1, -1, -1), // 11
								new Word(Opcode.DATA, -1, -1, -1), // 12
								new Word(Opcode.DATA, -1, -1, -1) // 13
						}),

				new Program("fibonacci10",
						new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
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
								new Word(Opcode.DATA, -1, -1, -1), // POS 20
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1) // ate aqui - serie de fibonacci ficara armazenada
						}),

				new Program("fibonacci10v2",
						new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
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
								new Word(Opcode.DATA, -1, -1, -1), // POS 20
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
								new Word(Opcode.DATA, -1, -1, -1) // ate aqui - serie de fibonacci ficara armazenada
						}),
				new Program("fibonacciREAD",
						new Word[] {
								// mesmo que prog exemplo, so que usa r0 no lugar de r8
								new Word(Opcode.LDI, 8, -1, 1), // leitura
								new Word(Opcode.LDI, 9, -1, 55), // endereco a guardar o tamanho da serie de fib a gerar
																	// - pode ser de 1 a 20
								new Word(Opcode.SYSCALL, -1, -1, -1),
								new Word(Opcode.LDD, 7, -1, 55),
								new Word(Opcode.LDI, 3, -1, 0),
								new Word(Opcode.ADD, 3, 7, -1),
								new Word(Opcode.LDI, 4, -1, 36), // posicao para qual ira pular (stop) *
								new Word(Opcode.LDI, 1, -1, -1), // caso negativo
								new Word(Opcode.STD, 1, -1, 41),
								new Word(Opcode.JMPIL, 4, 7, -1), // pula pra stop caso negativo *
								new Word(Opcode.JMPIE, 4, 7, -1), // pula pra stop caso 0
								new Word(Opcode.ADDI, 7, -1, 41), // fibonacci + posição do stop
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.STD, 1, -1, 41), // 25 posicao de memoria onde inicia a serie de
																	// fibonacci gerada
								new Word(Opcode.SUBI, 3, -1, 1), // se 1 pula pro stop
								new Word(Opcode.JMPIE, 4, 3, -1),
								new Word(Opcode.ADDI, 3, -1, 1),
								new Word(Opcode.LDI, 2, -1, 1),
								new Word(Opcode.STD, 2, -1, 42),
								new Word(Opcode.SUBI, 3, -1, 2), // se 2 pula pro stop
								new Word(Opcode.JMPIE, 4, 3, -1),
								new Word(Opcode.LDI, 0, -1, 43),
								new Word(Opcode.LDI, 6, -1, 25), // salva posição de retorno do loop
								new Word(Opcode.LDI, 5, -1, 0), // salva tamanho
								new Word(Opcode.ADD, 5, 7, -1),
								new Word(Opcode.LDI, 7, -1, 0), // zera (inicio do loop)
								new Word(Opcode.ADD, 7, 5, -1), // recarrega tamanho
								new Word(Opcode.LDI, 3, -1, 0),
								new Word(Opcode.ADD, 3, 1, -1),
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.ADD, 1, 2, -1),
								new Word(Opcode.ADD, 2, 3, -1),
								new Word(Opcode.STX, 0, 2, -1),
								new Word(Opcode.ADDI, 0, -1, 1),
								new Word(Opcode.SUB, 7, 0, -1),
								new Word(Opcode.JMPIG, 6, 7, -1), // volta para o inicio do loop
								new Word(Opcode.STOP, -1, -1, -1), // POS 36
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1), // POS 41
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
								// dado um inteiro em alguma posição de memória,
								// se for negativo armazena -1 na saída; se for positivo responde o fatorial do
								// número na saída
								new Word(Opcode.LDI, 0, -1, 7), // numero para colocar na memoria
								new Word(Opcode.STD, 0, -1, 50),
								new Word(Opcode.LDD, 0, -1, 50),
								new Word(Opcode.LDI, 1, -1, -1),
								new Word(Opcode.LDI, 2, -1, 13), // SALVAR POS STOP
								new Word(Opcode.JMPIL, 2, 0, -1), // caso negativo pula pro STD
								new Word(Opcode.LDI, 1, -1, 1),
								new Word(Opcode.LDI, 6, -1, 1),
								new Word(Opcode.LDI, 7, -1, 13),
								new Word(Opcode.JMPIE, 7, 0, 0), // POS 9 pula pra STD (Stop-1)
								new Word(Opcode.MULT, 1, 0, -1),
								new Word(Opcode.SUB, 0, 6, -1),
								new Word(Opcode.JMP, -1, -1, 9), // pula para o JMPIE
								new Word(Opcode.STD, 1, -1, 15),
								new Word(Opcode.STOP, -1, -1, -1), // POS 14
								new Word(Opcode.DATA, -1, -1, -1) // POS 15
						}),
				new Program("PC",
						new Word[] {
								// Para um N definido (10 por exemplo)
								// o programa ordena um vetor de N números em alguma posição de memória;
								// ordena usando bubble sort
								// loop ate que não swap nada
								// passando pelos N valores
								// faz swap de vizinhos se da esquerda maior que da direita
								new Word(Opcode.LDI, 7, -1, 5), // TAMANHO DO BUBBLE SORT (N)
								new Word(Opcode.LDI, 6, -1, 5), // aux N
								new Word(Opcode.LDI, 5, -1, 46), // LOCAL DA MEMORIA
								new Word(Opcode.LDI, 4, -1, 47), // aux local memoria
								new Word(Opcode.LDI, 0, -1, 4), // colocando valores na memoria
								new Word(Opcode.STD, 0, -1, 46),
								new Word(Opcode.LDI, 0, -1, 3),
								new Word(Opcode.STD, 0, -1, 47),
								new Word(Opcode.LDI, 0, -1, 5),
								new Word(Opcode.STD, 0, -1, 48),
								new Word(Opcode.LDI, 0, -1, 1),
								new Word(Opcode.STD, 0, -1, 49),
								new Word(Opcode.LDI, 0, -1, 2),
								new Word(Opcode.STD, 0, -1, 50), // colocando valores na memoria até aqui - POS 13
								new Word(Opcode.LDI, 3, -1, 25), // Posicao para pulo CHAVE 1
								new Word(Opcode.STD, 3, -1, 99),
								new Word(Opcode.LDI, 3, -1, 22), // Posicao para pulo CHAVE 2
								new Word(Opcode.STD, 3, -1, 98),
								new Word(Opcode.LDI, 3, -1, 38), // Posicao para pulo CHAVE 3
								new Word(Opcode.STD, 3, -1, 97),
								new Word(Opcode.LDI, 3, -1, 25), // Posicao para pulo CHAVE 4 (não usada)
								new Word(Opcode.STD, 3, -1, 96),
								new Word(Opcode.LDI, 6, -1, 0), // r6 = r7 - 1 POS 22
								new Word(Opcode.ADD, 6, 7, -1),
								new Word(Opcode.SUBI, 6, -1, 1), // ate aqui
								new Word(Opcode.JMPIEM, -1, 6, 97), // CHAVE 3 para pular quando r7 for 1 e r6 0 para
																	// interomper o loop de vez do programa
								new Word(Opcode.LDX, 0, 5, -1), // r0 e ra pegando valores das posições da memoria POS
																// 26
								new Word(Opcode.LDX, 1, 4, -1),
								new Word(Opcode.LDI, 2, -1, 0),
								new Word(Opcode.ADD, 2, 0, -1),
								new Word(Opcode.SUB, 2, 1, -1),
								new Word(Opcode.ADDI, 4, -1, 1),
								new Word(Opcode.SUBI, 6, -1, 1),
								new Word(Opcode.JMPILM, -1, 2, 99), // LOOP chave 1 caso neg procura prox
								new Word(Opcode.STX, 5, 1, -1),
								new Word(Opcode.SUBI, 4, -1, 1),
								new Word(Opcode.STX, 4, 0, -1),
								new Word(Opcode.ADDI, 4, -1, 1),
								new Word(Opcode.JMPIGM, -1, 6, 99), // LOOP chave 1 POS 38
								new Word(Opcode.ADDI, 5, -1, 1),
								new Word(Opcode.SUBI, 7, -1, 1),
								new Word(Opcode.LDI, 4, -1, 0), // r4 = r5 + 1 POS 41
								new Word(Opcode.ADD, 4, 5, -1),
								new Word(Opcode.ADDI, 4, -1, 1), // ate aqui
								new Word(Opcode.JMPIGM, -1, 7, 98), // LOOP chave 2
								new Word(Opcode.STOP, -1, -1, -1), // POS 45
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