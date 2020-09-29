#
# Copyright (c) 2008-2020 the Urho3D project.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#

task default: :build

desc 'Invoke CMake to configure and generate a build tree'
task :cmake => [:init] do
  if ENV['CI']
    system 'cmake --version' or abort 'Failed to find CMake'
  end
  next if ENV['PLATFORM'] == 'android' || (Dir.exist?("#{build_tree}") and not ARGV.include?('cmake'))
  script = "script/cmake_#{ENV['GENERATOR']}#{ENV['OS'] ? '.bat' : '.sh'}"
  build_options = /linux|macOS|win/ =~ ENV['PLATFORM'] ? '' : "-D #{ENV['PLATFORM'].upcase}=1"
  File.readlines('script/.build-options').each { |var|
    var.chomp!
    build_options = "#{build_options} -D #{var}=#{ENV[var]}" if ENV[var]
  }
  system %Q{#{script} "#{build_tree}" #{build_options}} or abort
end

desc 'Clean the build tree'
task :clean => [:init] do
  if ENV['PLATFORM'] == 'android'
    Rake::Task[:gradle].invoke('clean')
    next
  end
  system build_target('clean') or abort
end

desc 'Build the software'
task :build, [:target] => [:cmake] do |_, args|
  system "ccache -z" if ENV['USE_CCACHE']
  if ENV['PLATFORM'] == 'android'
    Rake::Task[:gradle].invoke('build -x test')
    system "ccache -s" if ENV['USE_CCACHE']
    next
  end
  filter = ''
  case ENV['GENERATOR']
  when 'xcode'
    concurrent = '' # Assume xcodebuild will do the right things without the '-jobs'
    filter = '|xcpretty -c && exit ${PIPESTATUS[0]}' if system('xcpretty -v >/dev/null 2>&1')
  when 'vs'
    concurrent = '/maxCpuCount'
  else
    concurrent = "-j #{$max_jobs}"
    filter = "2>#{lint_err_file}" if ENV['URHO3D_LINT']
  end
  system "#{build_target(args[:target])} -- #{concurrent} #{ENV['BUILD_PARAMS']} #{filter}" or abort
  system "ccache -s" if ENV['USE_CCACHE']
end

desc 'Test the software'
task :test => [:init] do
  if ENV['PLATFORM'] == 'android'
    Rake::Task[:gradle].invoke('test')
    next
  elsif ENV['URHO3D_LINT'] == '1'
    Rake::Task[:lint].invoke
    next
  elsif ENV['URHO3D_STYLE'] == '1'
    Rake::Task[:style].invoke
    next
  end
  wrapper = ENV['CI'] && ENV['PLATFORM'] == 'linux' ? 'xvfb-run' : ''
  test = /xcode|vs/ =~ ENV['GENERATOR'] ? 'RUN_TESTS' : 'test'
  system build_target(test, wrapper) or abort
end

desc 'Generate documentation'
task :doc => [:init] do
  if ENV['PLATFORM'] == 'android'
    Rake::Task[:gradle].invoke('documentationZip')
    next
  end
  system build_target('doc') or abort
end

desc 'Install the software'
task :install, [:prefix] => [:init] do |_, args|
  if ENV['PLATFORM'] == 'android'
    Rake::Task[:gradle].invoke('publishToMavenLocal')
    next
  end
  wrapper = args[:prefix] && !ENV['OS'] ? "DESTDIR=#{verify_path(args[:prefix])}" : ''
  system build_target('install', wrapper) or abort
end

desc 'Package build artifact'
task :package => [:init] do
  if ENV['PLATFORM'] == 'android'
    Rake::Task[:gradle].invoke('zipBuildTreeDebug zipBuildTreeRelease')
    next
  end
  wrapper = /linux|rpi|arm/ =~ ENV['PLATFORM'] && ENV['URHO3D_64BIT'] == '0' ? 'setarch i686' : ''
  system build_target('package', wrapper) or abort
end

desc 'Publish build artifact'
task :publish => [:init] do
  if ENV['PLATFORM'] == 'android'
    Rake::Task[:gradle].invoke('publish')
    next
  end
  abort "The 'publish' task is currently not supported on '#{ENV['PLATFORM']}' platform"
end

desc 'Create a new project'
task :new, [:name, :parent_dir, :use_copy] => [:init] do |_, args|
  args.with_defaults(:name => 'UrhoApp', :parent_dir => '~/projects', :use_copy => false)
  parent_dir = verify_path(args[:parent_dir])
  dir = "#{parent_dir}/#{args[:name]}"
  abort "The directory '#{dir}' already exists!" if Dir.exists?(dir)
  puts "Creating a new project in #{dir}..."
  FileUtils.mkdir_p(%W[#{dir}/src #{dir}/bin/Data])
  use_copy = args[:use_copy] || dockerized?
  func = FileUtils.method(use_copy ? :cp_r : :ln_s)
  func.call(verify_path('bin/CoreData'), "#{dir}/bin")
  %w[CMake Rakefile script].each { |it| func.call(verify_path(it), dir) }
  File.write("#{dir}/CMakeLists.txt", <<EOF)
cmake_minimum_required (VERSION 3.10.2)
project (#{args[:name]})
set (CMAKE_MODULE_PATH ${CMAKE_SOURCE_DIR}/CMake/Modules)
include (UrhoCommon)
set (TARGET_NAME #{args[:name]})
define_source_files (GLOB_CPP_PATTERNS src/*.cpp GLOB_H_PATTERNS src/*.h RECURSE GROUP)
setup_main_executable ()
setup_test ()
EOF
  %w[Source/Tools/Urho3DPlayer/Urho3DPlayer.cpp Source/Tools/Urho3DPlayer/Urho3DPlayer.h].each { |it| FileUtils.cp(it, "#{dir}/src") }
  puts "Done!"
end


### Internal tasks ###

task :ci do
  ENV['URHO3D_PCH'] = '0' if ENV['PLATFORM'] == 'linux-gcc' # TODO - PCH causes cache miss on initial build for Linux/GCC, why?
  platform_modifier = /(.+?)-(.+)/.match(ENV['PLATFORM'])
  if platform_modifier
    ENV['PLATFORM'] = platform_modifier[1]
    ENV['MODIFIER'] = platform_modifier[2]
  end
  case ENV['HOST']
  when 'macOS'
    ENV['GENERATOR'] = ENV['URHO3D_DEPLOYMENT_TARGET'] = 'generic' if ENV['MODIFIER'] == 'make'
    ENV['BUILD_PARAMS'] = '-sdk iphonesimulator' if ENV['PLATFORM'] == 'iOS'
    ENV['BUILD_PARAMS'] = '-sdk appletvsimulator' if ENV['PLATFORM'] == 'tvOS'
  when 'windows'
    if ENV['MODIFIER'] == 'gcc'
      ENV['URHO3D_DEPLOYMENT_TARGET'] = 'generic'
      ENV['GENERATOR'] = 'mingw'
    end
  else
    ENV['URHO3D_DEPLOYMENT_TARGET'] = 'generic' if /linux|mingw/ =~ ENV['PLATFORM']
    if /clang/ =~ ENV['MODIFIER']
      ENV['CC'] = 'clang'
      ENV['CXX'] = 'clang++'
    end
  end
  ENV['BUILD_TREE'] = 'build/ci'
  ENV['CMAKE_BUILD_TYPE'] = ENV['BUILD_TYPE'] == 'dbg' ? 'Debug' : 'Release' if /dbg|rel/ =~ ENV['BUILD_TYPE']
  case ENV['GRAPHICS_API']
  when 'DX11'
    ENV['URHO3D_D3D11'] = '1'
  when 'DX9'
    ENV['URHO3D_OPENGL'] = '0' # Need to make this explicit because 'MINGW' default to use OpenGL otherwise
  when 'OpenGL'
    ENV['URHO3D_OPENGL'] = '1'
  else
    # Do nothing
  end
  ENV['URHO3D_LIB_TYPE'] = ENV['LIB_TYPE'].upcase if /static|shared/ =~ ENV['LIB_TYPE']
  ENV['URHO3D_TESTING'] = '1' if /linux|macOS|win/ =~ ENV['PLATFORM']
  ENV['URHO3D_LINT'] = '1' if ENV['MODIFIER'] == 'clang-tidy'
  ENV['URHO3D_STYLE'] = '1' if ENV['MODIFIER'] == 'clang-format'
  # Enable all the bells and whistles
  %w[URHO3D_DATABASE_SQLITE URHO3D_EXTRAS].each { |it| ENV[it] = '1' }
end

task :source_checksum do
  require 'digest'
  sha256_final = Digest::SHA256.new
  sha256_iter = Digest::SHA256
  Dir['Source/**/*.{c,h}*'].each { |it| sha256_final << sha256_iter.file(it).hexdigest }
  puts "::set-output name=hexdigest::#{sha256_final.hexdigest}"
end

task :update_dot_files do
  system 'bash', '-c', %q{
    perl -ne 'undef $/; print $1 if /(Build Option.*?(?=\n\n))/s' Docs/GettingStarted.dox \
      |tail -n +3 |cut -d'|' -f2 |tr -d [:blank:] >script/.build-options && \
    echo URHO3D_LINT >>script/.build-options && \
    cat script/.build-options <(perl -ne 'while (/([A-Z_]+):.+?/g) {print "$1\n"}' .github/workflows/main.yml) \
      <(perl -ne 'while (/ENV\[\x27(\w+)\x27\]/g) {print "$1\n"}' Rakefile) \
      <(perl -ne 'while (/System.getenv\\("(\w+)"\\)/g) {print "$1\n"}' android/urho3d-lib/build.gradle.kts) \
      |sort |uniq |grep -Ev '^(HOME|PATH)$' >script/.env-file
  } or abort 'Failed to update dot files'
end

task :init do
  next if $max_jobs
  Rake::Task[:ci].invoke if ENV['CI']
  case build_host
  when /linux/
    $max_jobs = `grep -c processor /proc/cpuinfo`.chomp
    ENV['GENERATOR'] = 'generic' unless ENV['GENERATOR']
    ENV['PLATFORM'] = 'linux' unless ENV['PLATFORM']
  when /darwin|macOS/
    $max_jobs = `sysctl -n hw.logicalcpu`.chomp
    ENV['GENERATOR'] = 'xcode' unless ENV['GENERATOR']
    ENV['PLATFORM'] = 'macOS' unless ENV['PLATFORM']
  when /win32|mingw|mswin|windows/
    require 'win32ole'
    WIN32OLE.connect('winmgmts://').ExecQuery("select NumberOfLogicalProcessors from Win32_ComputerSystem").each { |it|
      $max_jobs = it.NumberOfLogicalProcessors
    }
    ENV['GENERATOR'] = 'vs' unless ENV['GENERATOR']
    ENV['PLATFORM'] = 'win' unless ENV['PLATFORM']
  else
    abort "Unsupported host system: #{build_host}"
  end
  # The 'ARCH' env-var, when set, has higher precedence than the 'URHO3D_64BIT' env-var
  ENV['URHO3D_64BIT'] = ENV['ARCH'] == '32' ? '0' : '1' if /32|64/ =~ ENV['ARCH']
end

task :gradle, [:task] do |_, args|
  system "./gradlew #{args[:task]} #{ENV['CI'] ? '--console plain' : ''}" or abort
end

task :lint do
  lint_err = File.read(lint_err_file)
  puts lint_err
  # TODO: Tighten the check by failing the job later
  # abort 'Failed to pass linter checks' unless lint_err.empty?
  # puts 'Passed the linter checks'
end

task :style do
  system 'bash', '-c', %q{
    git diff --name-only HEAD~ -- Source \
      |grep -v ThirdParty \
      |grep -P '\.(?:c|cpp|h|hpp)' \
      |xargs clang-format -n -Werror 2>&1 \
      |tee build/clang-format.out \
      && exit ${PIPESTATUS[3]}
  } or abort 'Failed to pass style checks'
  puts 'Passed the style checks'
end


### Internal methods ###

def build_host
  ENV['HOST'] || RUBY_PLATFORM
end

def build_tree
  ENV['BUILD_TREE'] || "build/#{dockerized? ? 'dockerized-' : ''}#{ENV['PLATFORM'].downcase}"
end

def build_config
  /xcode|vs/ =~ ENV['GENERATOR'] ? "--config #{ENV.fetch('CONFIG', 'Release')}" : ''
end

def build_target(tgt, wrapper = '')
  %Q{#{wrapper} cmake --build "#{build_tree}" #{build_config} #{tgt ? "--target #{tgt}" : ''}}
end

def lint_err_file
  'build/clang-tidy.out'
end

def verify_path(path)
  require 'pathname'
  begin
    Pathname.new(File.expand_path(path)).realdirpath.to_s
  rescue
    abort "The specified path '#{path}' is invalid!"
  end
end

def dockerized?
  File.exists?('/entrypoint.sh')
end


# Load custom rake scripts
Dir['.rake/*.rake'].each { |r| load r }

# vi: set ts=2 sw=2 expandtab:
