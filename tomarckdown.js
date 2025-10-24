// Arquivo: analyze-project.js
// Execute com: node analyze-project.js

const fs = require('fs');
const path = require('path');

// Configurações
const PROJECT_PATH = process.cwd(); // Diretório atual
const OUTPUT_FILE = 'project-analysis.md';

// Estrutura esperada do projeto
const EXPECTED_STRUCTURE = {
    'app/build.gradle': { required: true, type: 'file' },
    'app/src/main/AndroidManifest.xml': { required: true, type: 'file' },
    'app/src/main/java': { required: true, type: 'directory' },
    'app/src/main/res': { required: true, type: 'directory' },
    'build.gradle': { required: true, type: 'file' },
    'settings.gradle': { required: true, type: 'file' },
};

// Arquivos importantes do projeto Conversa
const IMPORTANT_FILES = {
    kotlin: [
        'ConversaApplication.kt',
        'MainActivity.kt',
        'ChatActivity.kt',
        'LoginActivity.kt',
        'SplashActivity.kt',
        'ChatViewModel.kt',
        'MainViewModel.kt',
        'LoginViewModel.kt',
        'WebSocketClient.kt',
        'ConversaApiService.kt',
        'ConversaDatabase.kt',
        'PreferencesManager.kt',
    ],
    layouts: [
        'activity_main.xml',
        'activity_chat.xml',
        'activity_login.xml',
        'activity_splash.xml',
        'item_conversa.xml',
        'item_message_sent.xml',
        'item_message_received.xml',
    ],
    resources: [
        'colors.xml',
        'themes.xml',
        'strings.xml',
        'styles.xml',
    ],
    drawables: [
        'bg_message_sent.xml',
        'bg_message_received.xml',
        'bg_message_input.xml',
        'bg_badge.xml',
    ]
};

class ProjectAnalyzer {
    constructor() {
        this.report = [];
        this.issues = [];
        this.warnings = [];
        this.fileMap = new Map();
        this.packageName = '';
    }

    // Adiciona linha ao relatório
    addLine(text = '') {
        this.report.push(text);
    }

    // Adiciona issue
    addIssue(issue) {
        this.issues.push(issue);
    }

    // Adiciona warning
    addWarning(warning) {
        this.warnings.push(warning);
    }

    // Percorre diretório recursivamente
    walkDir(dir, fileList = []) {
        const files = fs.readdirSync(dir);
        
        files.forEach(file => {
            const filePath = path.join(dir, file);
            const stat = fs.statSync(filePath);
            
            if (stat.isDirectory()) {
                if (!file.startsWith('.') && file !== 'build') {
                    this.walkDir(filePath, fileList);
                }
            } else {
                const relativePath = path.relative(PROJECT_PATH, filePath);
                fileList.push(relativePath);
                this.fileMap.set(file, relativePath);
            }
        });
        
        return fileList;
    }

    // Verifica estrutura básica
    checkBasicStructure() {
        this.addLine('## 📁 Estrutura Básica do Projeto\n');
        
        for (const [itemPath, config] of Object.entries(EXPECTED_STRUCTURE)) {
            const fullPath = path.join(PROJECT_PATH, itemPath);
            const exists = fs.existsSync(fullPath);
            
            if (exists) {
                this.addLine(`✅ ${itemPath}`);
            } else {
                this.addLine(`❌ ${itemPath} - **AUSENTE**`);
                if (config.required) {
                    this.addIssue(`Arquivo/Diretório obrigatório ausente: ${itemPath}`);
                }
            }
        }
        
        this.addLine('');
    }

    // Analisa build.gradle
    analyzeBuildGradle() {
        this.addLine('## 🔧 Análise do build.gradle\n');
        
        const buildGradlePath = path.join(PROJECT_PATH, 'app/build.gradle');
        
        if (!fs.existsSync(buildGradlePath)) {
            this.addIssue('app/build.gradle não encontrado');
            return;
        }
        
        const content = fs.readFileSync(buildGradlePath, 'utf8');
        
        // Verificações
        const checks = {
            'Hilt Plugin': content.includes('dagger.hilt'),
            'Kapt Plugin': content.includes('kotlin-kapt'),
            'ViewBinding': content.includes('viewBinding'),
            'BuildConfig': content.includes('buildConfig'),
            'Hilt Dependency': content.includes('hilt-android'),
            'Room Dependency': content.includes('room-runtime'),
            'Retrofit Dependency': content.includes('retrofit'),
            'Coroutines Dependency': content.includes('kotlinx-coroutines'),
            'Package Name': /namespace\s+['"]([^'"]+)['"]/.test(content),
        };
        
        for (const [check, passed] of Object.entries(checks)) {
            if (passed) {
                this.addLine(`✅ ${check}`);
            } else {
                this.addLine(`❌ ${check} - **AUSENTE**`);
                this.addWarning(`${check} não encontrado em build.gradle`);
            }
        }
        
        // Extrair package name
        const packageMatch = content.match(/namespace\s+['"]([^'"]+)['"]/);
        if (packageMatch) {
            this.packageName = packageMatch[1];
            this.addLine(`\n📦 **Package Name:** \`${this.packageName}\``);
        } else {
            this.addWarning('Package name não encontrado');
        }
        
        this.addLine('');
    }

    // Analisa AndroidManifest.xml
    analyzeManifest() {
        this.addLine('## 📱 Análise do AndroidManifest.xml\n');
        
        const manifestPath = path.join(PROJECT_PATH, 'app/src/main/AndroidManifest.xml');
        
        if (!fs.existsSync(manifestPath)) {
            this.addIssue('AndroidManifest.xml não encontrado');
            return;
        }
        
        const content = fs.readFileSync(manifestPath, 'utf8');
        
        const checks = {
            'Application Class': content.includes('android:name=".ConversaApplication"'),
            'Internet Permission': content.includes('android.permission.INTERNET'),
            'Camera Permission': content.includes('android.permission.CAMERA'),
            'Storage Permission': content.includes('READ_EXTERNAL_STORAGE'),
            'MainActivity': content.includes('MainActivity'),
            'ChatActivity': content.includes('ChatActivity'),
            'LoginActivity': content.includes('LoginActivity'),
            'SplashActivity': content.includes('SplashActivity'),
            'WebSocketService': content.includes('WebSocketService'),
            'FileProvider': content.includes('FileProvider'),
        };
        
        for (const [check, passed] of Object.entries(checks)) {
            if (passed) {
                this.addLine(`✅ ${check}`);
            } else {
                this.addLine(`⚠️ ${check} - **AUSENTE**`);
                this.addWarning(`${check} não encontrado no Manifest`);
            }
        }
        
        this.addLine('');
    }

    // Verifica arquivos Kotlin importantes
    checkKotlinFiles() {
        this.addLine('## 🔷 Arquivos Kotlin (.kt)\n');
        
        const kotlinFiles = Array.from(this.fileMap.entries())
            .filter(([file]) => file.endsWith('.kt'))
            .map(([file, path]) => ({ file, path }));
        
        this.addLine(`**Total encontrado:** ${kotlinFiles.length}\n`);
        
        // Verifica arquivos importantes
        this.addLine('### Arquivos Importantes:\n');
        
        for (const importantFile of IMPORTANT_FILES.kotlin) {
            const found = kotlinFiles.find(f => f.file === importantFile);
            
            if (found) {
                this.addLine(`✅ ${importantFile} - \`${found.path}\``);
            } else {
                this.addLine(`❌ ${importantFile} - **AUSENTE**`);
                this.addIssue(`Arquivo Kotlin ausente: ${importantFile}`);
            }
        }
        
        this.addLine('');
    }

    // Verifica layouts XML
    checkLayouts() {
        this.addLine('## 📐 Layouts XML\n');
        
        const layoutFiles = Array.from(this.fileMap.entries())
            .filter(([file, filePath]) => filePath.includes('res/layout/'))
            .map(([file, path]) => ({ file, path }));
        
        this.addLine(`**Total encontrado:** ${layoutFiles.length}\n`);
        
        for (const importantFile of IMPORTANT_FILES.layouts) {
            const found = layoutFiles.find(f => f.file === importantFile);
            
            if (found) {
                this.addLine(`✅ ${importantFile}`);
            } else {
                this.addLine(`❌ ${importantFile} - **AUSENTE**`);
                this.addIssue(`Layout ausente: ${importantFile}`);
            }
        }
        
        this.addLine('');
    }

    // Verifica resources (colors, strings, themes)
    checkResources() {
        this.addLine('## 🎨 Recursos (values)\n');
        
        for (const resource of IMPORTANT_FILES.resources) {
            const found = this.fileMap.has(resource);
            
            if (found) {
                this.addLine(`✅ ${resource} - \`${this.fileMap.get(resource)}\``);
            } else {
                this.addLine(`❌ ${resource} - **AUSENTE**`);
                this.addIssue(`Arquivo de recurso ausente: ${resource}`);
            }
        }
        
        // Verifica colors.xml especificamente
        if (this.fileMap.has('colors.xml')) {
            const colorsPath = path.join(PROJECT_PATH, this.fileMap.get('colors.xml'));
            const content = fs.readFileSync(colorsPath, 'utf8');
            
            this.addLine('\n### Cores Verificadas em colors.xml:\n');
            
            const requiredColors = [
                'colorPrimary',
                'colorPrimaryDark',
                'colorAccent',
                'white',
                'text_primary',
                'message_sent_bg',
                'message_received_bg'
            ];
            
            for (const color of requiredColors) {
                if (content.includes(`name="${color}"`)) {
                    this.addLine(`✅ ${color}`);
                } else {
                    this.addLine(`❌ ${color} - **AUSENTE**`);
                    this.addWarning(`Cor ${color} não encontrada em colors.xml`);
                }
            }
        }
        
        this.addLine('');
    }

    // Verifica drawables
    checkDrawables() {
        this.addLine('## 🖼️ Drawables\n');
        
        const drawableFiles = Array.from(this.fileMap.entries())
            .filter(([file, filePath]) => filePath.includes('res/drawable'))
            .map(([file]) => file);
        
        this.addLine(`**Total encontrado:** ${drawableFiles.length}\n`);
        
        for (const drawable of IMPORTANT_FILES.drawables) {
            const found = drawableFiles.includes(drawable);
            
            if (found) {
                this.addLine(`✅ ${drawable}`);
            } else {
                this.addLine(`❌ ${drawable} - **AUSENTE**`);
                this.addWarning(`Drawable ausente: ${drawable}`);
            }
        }
        
        this.addLine('');
    }

    // Verifica imports nos arquivos Kotlin
    checkImports() {
        this.addLine('## 📦 Análise de Imports\n');
        
        const kotlinFiles = Array.from(this.fileMap.entries())
            .filter(([file]) => file.endsWith('.kt'));
        
        const importIssues = [];
        
        for (const [file, filePath] of kotlinFiles) {
            const fullPath = path.join(PROJECT_PATH, filePath);
            const content = fs.readFileSync(fullPath, 'utf8');
            
            // Verifica package name incorreto
            if (this.packageName && !content.includes(`package ${this.packageName}`)) {
                const packageMatch = content.match(/package\s+([^\s]+)/);
                if (packageMatch && packageMatch[1] !== this.packageName) {
                    importIssues.push(`${file}: Package incorreto - esperado ${this.packageName}, encontrado ${packageMatch[1]}`);
                }
            }
            
            // Verifica imports não resolvidos (aproximado)
            const importLines = content.match(/import\s+[^\n]+/g) || [];
            for (const importLine of importLines) {
                if (importLine.includes('com.seudominio') && this.packageName) {
                    importIssues.push(`${file}: Import com package genérico - ${importLine.trim()}`);
                }
            }
        }
        
        if (importIssues.length > 0) {
            this.addLine('### ⚠️ Problemas de Import Encontrados:\n');
            importIssues.forEach(issue => {
                this.addLine(`- ${issue}`);
                this.addWarning(issue);
            });
        } else {
            this.addLine('✅ Nenhum problema de import detectado');
        }
        
        this.addLine('');
    }

    // Gera resumo
    generateSummary() {
        this.addLine('## 📊 Resumo da Análise\n');
        
        const totalFiles = this.fileMap.size;
        const kotlinFiles = Array.from(this.fileMap.keys()).filter(f => f.endsWith('.kt')).length;
        const xmlFiles = Array.from(this.fileMap.keys()).filter(f => f.endsWith('.xml')).length;
        
        this.addLine(`- **Total de arquivos:** ${totalFiles}`);
        this.addLine(`- **Arquivos Kotlin (.kt):** ${kotlinFiles}`);
        this.addLine(`- **Arquivos XML:** ${xmlFiles}`);
        this.addLine(`- **Issues críticos:** ${this.issues.length}`);
        this.addLine(`- **Warnings:** ${this.warnings.length}`);
        
        if (this.issues.length > 0) {
            this.addLine('\n### ❌ Issues Críticos:\n');
            this.issues.forEach((issue, i) => {
                this.addLine(`${i + 1}. ${issue}`);
            });
        }
        
        if (this.warnings.length > 0) {
            this.addLine('\n### ⚠️ Warnings:\n');
            this.warnings.forEach((warning, i) => {
                this.addLine(`${i + 1}. ${warning}`);
            });
        }
        
        this.addLine('');
    }

    // Gera recomendações
    generateRecommendations() {
        this.addLine('## 💡 Recomendações\n');
        
        if (this.issues.length === 0 && this.warnings.length === 0) {
            this.addLine('✅ **Projeto parece estar completo!**');
            this.addLine('\n**Próximos passos:**');
            this.addLine('1. Sync do Gradle');
            this.addLine('2. Build → Clean Project');
            this.addLine('3. Build → Rebuild Project');
            this.addLine('4. Testar no emulador/dispositivo');
        } else {
            this.addLine('**Ações recomendadas:**\n');
            
            if (this.issues.length > 0) {
                this.addLine('### Crítico (Deve corrigir):');
                this.issues.forEach((issue, i) => {
                    this.addLine(`${i + 1}. ${issue}`);
                });
                this.addLine('');
            }
            
            if (this.warnings.length > 0) {
                this.addLine('### Importante (Recomendado corrigir):');
                this.warnings.forEach((warning, i) => {
                    this.addLine(`${i + 1}. ${warning}`);
                });
            }
        }
        
        this.addLine('');
    }

    // Executa análise completa
    async analyze() {
        console.log('🔍 Analisando projeto...\n');
        
        // Header
        this.addLine('# 📱 Análise do Projeto Conversa Android\n');
        this.addLine(`**Data da análise:** ${new Date().toLocaleString('pt-BR')}\n`);
        this.addLine(`**Diretório:** \`${PROJECT_PATH}\`\n`);
        this.addLine('---\n');
        
        // Mapeia todos os arquivos
        console.log('📂 Mapeando arquivos...');
        this.walkDir(PROJECT_PATH);
        
        // Executa verificações
        console.log('✓ Verificando estrutura básica...');
        this.checkBasicStructure();
        
        console.log('✓ Analisando build.gradle...');
        this.analyzeBuildGradle();
        
        console.log('✓ Analisando AndroidManifest.xml...');
        this.analyzeManifest();
        
        console.log('✓ Verificando arquivos Kotlin...');
        this.checkKotlinFiles();
        
        console.log('✓ Verificando layouts...');
        this.checkLayouts();
        
        console.log('✓ Verificando resources...');
        this.checkResources();
        
        console.log('✓ Verificando drawables...');
        this.checkDrawables();
        
        console.log('✓ Analisando imports...');
        this.checkImports();
        
        console.log('✓ Gerando resumo...');
        this.generateSummary();
        
        console.log('✓ Gerando recomendações...');
        this.generateRecommendations();
        
        // Salva relatório
        const reportContent = this.report.join('\n');
        fs.writeFileSync(OUTPUT_FILE, reportContent);
        
        console.log(`\n✅ Análise concluída!`);
        console.log(`📄 Relatório salvo em: ${OUTPUT_FILE}`);
        console.log(`\n📊 Estatísticas:`);
        console.log(`   - Issues críticos: ${this.issues.length}`);
        console.log(`   - Warnings: ${this.warnings.length}`);
        console.log(`   - Arquivos analisados: ${this.fileMap.size}`);
    }
}

// Executa
const analyzer = new ProjectAnalyzer();
analyzer.analyze().catch(console.error);